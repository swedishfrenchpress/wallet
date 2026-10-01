package com.cashu.me.Core.Fedimint

import com.cashu.me.Models.FederationGuardian
import com.cashu.me.Models.GuardianHealth
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The guardians' public API, for what the Fedimint SDK doesn't surface.
 *
 * Every guardian serves unauthenticated JSON-RPC over WebSocket. Two methods
 * answer what the federation screen needs: `client_config` lists every
 * guardian (name and API address) and the configuration metadata, and
 * `status` is that guardian's view of consensus: which peers are connected
 * and which are flagged for not contributing to recent sessions.
 *
 * The parsing and merging below are pure so they can be tested against
 * recorded responses; [Client] is the thin network layer.
 */
object FedimintGuardianApi {
    private val json = Json { ignoreUnknownKeys = true }

    data class ClientConfig(val guardians: List<FederationGuardian>, val meta: Map<String, String>)

    /** One guardian's answer to `status`, from its own seat in consensus. */
    data class StatusReport(
        val peerId: Int,
        val consensusRunning: Boolean,
        val sessionCount: Long?,
        val peers: Map<Int, PeerView>,
    )

    data class PeerView(val connected: Boolean, val flagged: Boolean)

    data class Probe(val guardians: List<FederationGuardian>, val sessionCount: Long?, val configMeta: Map<String, String>)

    fun parseClientConfig(result: JsonElement): ClientConfig {
        val root = result.jsonObject
        val guardians = (root["api_endpoints"] as? JsonObject).orEmpty().mapNotNull { (peer, value) ->
            val endpoint = value as? JsonObject ?: return@mapNotNull null
            val url = endpoint.string("url") ?: return@mapNotNull null
            FederationGuardian(peerId = peer.toIntOrNull() ?: return@mapNotNull null, url = url, name = endpoint.string("name"))
        }.sortedBy { it.peerId }
        return ClientConfig(guardians, stringMap(root["meta"]))
    }

    fun parseStatus(peerId: Int, result: JsonElement): StatusReport {
        val root = result.jsonObject
        val federation = root["federation"] as? JsonObject
        val peers = (federation?.get("status_by_peer") as? JsonObject).orEmpty().mapNotNull { (peer, value) ->
            val view = value as? JsonObject ?: return@mapNotNull null
            val id = peer.toIntOrNull() ?: return@mapNotNull null
            id to PeerView(
                connected = view.string("connection_status").equals("connected", ignoreCase = true),
                flagged = (view["flagged"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }.toMap()
        return StatusReport(
            peerId = peerId,
            consensusRunning = root.string("server") == "consensus_running",
            sessionCount = (federation?.get("session_count") as? JsonPrimitive)?.longOrNull,
            peers = peers,
        )
    }

    /**
     * Health per guardian, from every `status` that came back. A guardian is
     * online when it answered us itself or any guardian sees it connected;
     * it is behind when it answered without running consensus or a peer
     * flags it. With no reports at all nothing is known.
     */
    fun roster(endpoints: List<FederationGuardian>, reports: List<StatusReport>): List<FederationGuardian> {
        if (reports.isEmpty()) return endpoints.map { it.copy(health = GuardianHealth.Unknown) }
        val responders = reports.associateBy { it.peerId }
        return endpoints.map { guardian ->
            val own = responders[guardian.peerId]
            val views = reports.mapNotNull { it.peers[guardian.peerId] }
            val health = when {
                own == null && views.isEmpty() -> GuardianHealth.Unknown
                own == null && views.none { it.connected } -> GuardianHealth.Offline
                own?.consensusRunning == false -> GuardianHealth.Behind
                views.any { it.connected && it.flagged } -> GuardianHealth.Behind
                else -> GuardianHealth.Active
            }
            guardian.copy(health = health)
        }
    }

    /**
     * Fedimint's legacy external meta: a JSON object keyed by federation id
     * whose values are flat string maps. Anything else contributes nothing.
     */
    fun parseExternalMeta(body: String, federationId: String): Map<String, String> = runCatching {
        stringMap(json.parseToJsonElement(body).jsonObject[federationId])
    }.getOrDefault(emptyMap())

    /** Where a federation publishes external meta, from its configuration meta. */
    fun externalMetaUrl(configMeta: Map<String, String>): String? =
        (configMeta["meta_override_url"] ?: configMeta["meta_external_url"])
            ?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) }

    /**
     * Configuration, then external meta, then consensus, each overriding the
     * last per key, as the Fedimint client ranks them. [sdkMerged] is the
     * SDK's consensus-over-configuration view; its consensus keys are the
     * ones that differ from [config].
     */
    fun mergeMeta(
        config: Map<String, String>,
        external: Map<String, String>,
        sdkMerged: Map<String, String>,
    ): Map<String, String> {
        val result = LinkedHashMap(config)
        result.putAll(external)
        sdkMerged.forEach { (key, value) -> if (config[key] != value) result[key] = value }
        return result
    }

    private fun stringMap(element: JsonElement?): Map<String, String> =
        (element as? JsonObject).orEmpty().mapNotNull { (key, value) ->
            when (value) {
                is JsonNull -> null
                is JsonPrimitive -> value.contentOrNull?.let { key to it }
                is JsonObject, is JsonArray -> key to value.toString()
            }
        }.toMap()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    class Client(private val http: OkHttpClient = defaultHttp) {
        /**
         * The full roster with health. The configuration comes from the first
         * listed guardian that answers; `status` then goes to every guardian
         * at once, so one slow or unreachable guardian costs one timeout.
         */
        suspend fun probe(known: List<FederationGuardian>): Probe? = coroutineScope {
            if (known.isEmpty()) return@coroutineScope null
            var config: ClientConfig? = null
            for (guardian in known) {
                config = orNull { parseClientConfig(call(guardian.url, "client_config")) }
                if (config != null) break
            }
            val endpoints = config?.guardians?.takeIf { it.isNotEmpty() } ?: known
            val reports = endpoints.map { guardian ->
                async { orNull { parseStatus(guardian.peerId, call(guardian.url, "status")) } }
            }.awaitAll().filterNotNull()
            Probe(
                guardians = roster(endpoints, reports),
                sessionCount = reports.mapNotNull { it.sessionCount }.maxOrNull(),
                configMeta = config?.meta.orEmpty(),
            )
        }

        suspend fun externalMeta(url: String, federationId: String): Map<String, String> =
            parseExternalMeta(get(url), federationId)

        /** One JSON-RPC request over a fresh WebSocket; returns `result` or throws. */
        suspend fun call(url: String, method: String, timeoutMillis: Long = CALL_TIMEOUT_MILLIS): JsonElement =
            withTimeout(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    val request = Request.Builder().url(url).build()
                    val socket = http.newWebSocket(request, object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            webSocket.send(
                                """{"jsonrpc":"2.0","id":1,"method":"$method","params":[{"auth":null,"params":null}]}""",
                            )
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val reply = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                            val result = reply?.get("result")
                            webSocket.close(NORMAL_CLOSURE, null)
                            if (!continuation.isActive) return
                            if (result != null && result !is JsonNull) {
                                continuation.resume(result)
                            } else {
                                continuation.resumeWithException(IOException("Guardian refused $method: ${reply?.get("error")}"))
                            }
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            if (continuation.isActive) continuation.resumeWithException(t)
                        }
                    })
                    continuation.invokeOnCancellation { socket.cancel() }
                }
            }

        private suspend fun get(url: String): String = withTimeout(CALL_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val call = http.newCall(Request.Builder().url(url).build())
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val body = response.use { if (it.isSuccessful) it.body?.string() else null }
                        if (!continuation.isActive) return
                        if (body != null) continuation.resume(body)
                        else continuation.resumeWithException(IOException("HTTP ${response.code} for $url"))
                    }
                })
                continuation.invokeOnCancellation { call.cancel() }
            }
        }

        private suspend fun <T> orNull(block: suspend () -> T): T? = try {
            block()
        } catch (cancellation: CancellationException) {
            // A per-call timeout is a failed guardian, not a cancelled probe.
            if (cancellation is kotlinx.coroutines.TimeoutCancellationException) null else throw cancellation
        } catch (error: Exception) {
            null
        }

        private companion object {
            const val CALL_TIMEOUT_MILLIS = 5_000L
            const val NORMAL_CLOSURE = 1000
            val defaultHttp: OkHttpClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }
}
