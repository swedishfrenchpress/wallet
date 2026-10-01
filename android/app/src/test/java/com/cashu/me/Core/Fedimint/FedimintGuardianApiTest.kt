package com.cashu.me.Core.Fedimint

import com.cashu.me.Core.Fedimint.FedimintGuardianApi.PeerView
import com.cashu.me.Core.Fedimint.FedimintGuardianApi.StatusReport
import com.cashu.me.Models.FederationGuardian
import com.cashu.me.Models.GuardianHealth
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FedimintGuardianApiTest {
    @Test
    fun clientConfigListsEveryGuardianWithItsName() {
        // Recorded from a Bitcoin Principles guardian (modules trimmed).
        val config = FedimintGuardianApi.parseClientConfig(
            Json.parseToJsonElement(
                """{"api_endpoints":{"0":{"name":"Guardian 1","url":"wss://api.bitcoin-principles.com/"},
                "1":{"name":"Guardian 4","url":"wss://api.bitcoinprinciples.xyz/"},
                "2":{"name":"Guardian 3","url":"wss://api.bitcoinprinciples.net/"},
                "3":{"name":"guardian 2","url":"wss://api.bitcoinprinciples.dev/"}},
                "consensus_version":{"major":2,"minor":0},
                "meta":{"federation_name":"Bitcoin Principles","meta_external_url":"https://meta.bitcoinprinciples.dev/meta.json"},
                "modules":{}}""",
            ),
        )

        assertEquals(listOf(0, 1, 2, 3), config.guardians.map { it.peerId })
        assertEquals(listOf("Guardian 1", "Guardian 4", "Guardian 3", "guardian 2"), config.guardians.map { it.name })
        assertEquals("wss://api.bitcoinprinciples.net/", config.guardians[2].url)
        assertEquals("https://meta.bitcoinprinciples.dev/meta.json", config.meta["meta_external_url"])
    }

    @Test
    fun statusReadsConsensusViewOfThePeers() {
        // Recorded from guardian 2 of Bitcoin Principles.
        val report = FedimintGuardianApi.parseStatus(
            peerId = 2,
            result = Json.parseToJsonElement(
                """{"federation":{"peers_flagged":0,"peers_offline":0,"peers_online":3,"scheduled_shutdown":null,
                "session_count":445142,"status_by_peer":{
                "0":{"connection_status":"connected","flagged":false,"last_contribution":445142},
                "1":{"connection_status":"connected","flagged":false,"last_contribution":445142},
                "3":{"connection_status":"disconnected","flagged":true,"last_contribution":445100}}},
                "server":"consensus_running"}""",
            ),
        )

        assertTrue(report.consensusRunning)
        assertEquals(445_142L, report.sessionCount)
        assertEquals(PeerView(connected = true, flagged = false), report.peers[0])
        assertEquals(PeerView(connected = false, flagged = true), report.peers[3])
    }

    @Test
    fun statusWithoutConsensusIsNotRunning() {
        val report = FedimintGuardianApi.parseStatus(1, Json.parseToJsonElement("""{"server":"awaiting_password"}"""))

        assertEquals(false, report.consensusRunning)
        assertNull(report.sessionCount)
        assertTrue(report.peers.isEmpty())
    }

    @Test
    fun rosterTrustsConsensusOverOurOwnReach() {
        val endpoints = (0..3).map { FederationGuardian(it, "wss://g$it.example/") }
        // Guardian 1's API is unreachable from here, but its peers see it connected and signing.
        val reports = listOf(
            report(0, 1 to view(), 2 to view(), 3 to view()),
            report(2, 0 to view(), 1 to view(), 3 to view()),
        )

        val health = FedimintGuardianApi.roster(endpoints, reports).map { it.health }

        assertEquals(List(4) { GuardianHealth.Active }, health)
    }

    @Test
    fun rosterMarksOfflineBehindAndUnknown() {
        val endpoints = (0..4).map { FederationGuardian(it, "wss://g$it.example/") }
        val reports = listOf(
            report(0, 1 to view(connected = false), 2 to view(flagged = true), 3 to view()),
            report(3, 0 to view(), 1 to view(connected = false), 2 to view()),
            StatusReport(peerId = 4, consensusRunning = false, sessionCount = null, peers = emptyMap()),
        )

        val health = FedimintGuardianApi.roster(endpoints, reports).map { it.health }

        assertEquals(
            listOf(
                GuardianHealth.Active, // answered, seen connected
                GuardianHealth.Offline, // nobody reaches it
                GuardianHealth.Behind, // connected but flagged by a peer
                GuardianHealth.Active,
                GuardianHealth.Behind, // answered, but not running consensus
            ),
            health,
        )
    }

    @Test
    fun rosterWithoutRepliesKnowsNothing() {
        val endpoints = listOf(FederationGuardian(0, "wss://g0.example/"))

        assertEquals(GuardianHealth.Unknown, FedimintGuardianApi.roster(endpoints, emptyList()).single().health)
    }

    @Test
    fun externalMetaTakesThisFederationsEntryAndSkipsNulls() {
        val body = """{"b21068c8":{"federation_icon_url":"https://icons.example/bp.png","welcome_message":"Hi",
            "chat_server_domain":null,"sites":[{"id":"x"}]},"other":{"federation_icon_url":"https://icons.example/o.png"}}"""

        val meta = FedimintGuardianApi.parseExternalMeta(body, "b21068c8")

        assertEquals("https://icons.example/bp.png", meta["federation_icon_url"])
        assertEquals("Hi", meta["welcome_message"])
        assertEquals("""[{"id":"x"}]""", meta["sites"])
        assertTrue("chat_server_domain" !in meta)
        assertTrue(FedimintGuardianApi.parseExternalMeta("not json", "b21068c8").isEmpty())
        assertTrue(FedimintGuardianApi.parseExternalMeta(body, "missing").isEmpty())
    }

    @Test
    fun externalMetaUrlPrefersOverrideAndRequiresHttps() {
        assertEquals(
            "https://override.example/meta.json",
            FedimintGuardianApi.externalMetaUrl(
                mapOf("meta_external_url" to "https://ext.example/meta.json", "meta_override_url" to "https://override.example/meta.json"),
            ),
        )
        assertNull(FedimintGuardianApi.externalMetaUrl(mapOf("meta_external_url" to "http://ext.example/meta.json")))
        assertNull(FedimintGuardianApi.externalMetaUrl(emptyMap()))
    }

    @Test
    fun metaPrecedenceIsConfigThenExternalThenConsensus() {
        val config = mapOf("federation_name" to "Config", "welcome_message" to "Config hello", "tos_url" to "https://config/tos")
        val external = mapOf("welcome_message" to "External hello", "federation_icon_url" to "https://ext/icon.png", "tos_url" to "https://ext/tos")
        // The SDK's view: consensus overrode tos_url, the rest echoes config.
        val sdkMerged = config + ("tos_url" to "https://consensus/tos")

        val merged = FedimintGuardianApi.mergeMeta(config, external, sdkMerged)

        assertEquals("Config", merged["federation_name"])
        assertEquals("External hello", merged["welcome_message"])
        assertEquals("https://ext/icon.png", merged["federation_icon_url"])
        assertEquals("https://consensus/tos", merged["tos_url"])
    }

    private fun view(connected: Boolean = true, flagged: Boolean = false) = PeerView(connected, flagged)

    private fun report(peerId: Int, vararg peers: Pair<Int, PeerView>) =
        StatusReport(peerId = peerId, consensusRunning = true, sessionCount = 445_142, peers = peers.toMap())
}
