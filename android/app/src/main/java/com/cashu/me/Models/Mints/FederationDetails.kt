package com.cashu.me.Models

import java.net.URI

/**
 * What the Fedimint SDK reports about one joined federation, in flavor-neutral
 * types so main code can render it without linking the SDK.
 *
 * Identity, network, capabilities, status, the invite and the configuration
 * metadata are local reads. [guardianCount], [modules], the consensus side of
 * [meta] and [metaRevision] need a round trip to the guardians, so they are
 * null/empty until a live fetch lands.
 */
data class FederationDetails(
    val federationId: String,
    val name: String?,
    val network: FederationNetwork,
    val state: FederationState,
    val supportsEcash: Boolean,
    val supportsLightning: Boolean,
    val supportsOnchain: Boolean,
    val inviteCode: String,
    /** Guardians whose endpoint the invite names; usually a subset of all guardians. */
    val inviteGuardians: List<FederationGuardian>,
    val guardianCount: Int? = null,
    /** Every guardian with its name and health, from the guardians' own API; empty until probed. */
    val guardianRoster: List<FederationGuardian> = emptyList(),
    /** The consensus session the guardians are on, when one answered `status`. */
    val sessionCount: Long? = null,
    /** Every module kind the federation runs (`mint`, `ln`, `wallet`, `meta`, …). */
    val modules: List<String> = emptyList(),
    /** Merged metadata: consensus values override configuration values per key. */
    val meta: Map<String, String> = emptyMap(),
    val metaRevision: Long? = null,
) {
    val quorum: FederationQuorum?
        get() = (guardianCount ?: guardianRoster.size.takeIf { it > 0 })?.takeIf { it > 0 }?.let(::FederationQuorum)

    /** The roster when probed, else the invite's guardians; in name order (`Guardian 2` before `Guardian 10`). */
    val guardians: List<FederationGuardian>
        get() = guardianRoster.ifEmpty { inviteGuardians }.sortedWith(GuardianNameOrder)

    val healthKnown: Boolean get() = guardianRoster.any { it.health != GuardianHealth.Unknown }
    val onlineCount: Int get() = guardianRoster.count { it.health == GuardianHealth.Active || it.health == GuardianHealth.Behind }
    val activeCount: Int get() = guardianRoster.count { it.health == GuardianHealth.Active }

    val welcomeMessage: String? get() = metaValue("welcome_message")
    val iconUrl: String? get() = metaValue("federation_icon_url")
    val tosUrl: String? get() = metaValue("tos_url")
    val successorInvite: String? get() = metaValue("federation_successor")
    val expiresAtEpochSeconds: Long? get() = metaValue("federation_expiry_timestamp")?.toLongOrNull()
    val maxBalanceSats: Long? get() = metaValue("max_balance_msats")?.toLongOrNull()?.div(MSATS_PER_SAT)
    val maxInvoiceSats: Long? get() = metaValue("max_invoice_msats")?.toLongOrNull()?.div(MSATS_PER_SAT)

    val previewMessage: String? get() = metaValue("preview_message")

    /** What the federation says about itself: the welcome message, else the preview; blank-line runs collapsed. */
    val aboutMessage: String?
        get() = (welcomeMessage ?: previewMessage)
            ?.replace("\r\n", "\n")
            ?.replace(Regex("\n{3,}"), "\n\n")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** When a guardians' countdown announcement runs out; Fedi uses it for a federation's end. */
    val popupEndsAtEpochSeconds: Long? get() = metaValue("popup_end_timestamp")?.toLongOrNull()

    /** When the federation stops: its declared expiry, else the end of the countdown announcement. */
    val endsAtEpochSeconds: Long? get() = expiresAtEpochSeconds ?: popupEndsAtEpochSeconds

    /** What the guardians say once the countdown is over. */
    val endedMessage: String? get() = metaValue("popup_ended_message")

    /** A guardian announcement with a countdown, while it is still running. */
    fun activePopup(nowEpochSeconds: Long): String? {
        val end = popupEndsAtEpochSeconds ?: return null
        if (end <= nowEpochSeconds) return null
        return metaValue("popup_countdown_message")
    }

    /**
     * Scalar metadata the screen doesn't already show, by key. Structured
     * values (JSON objects and arrays such as Fedi's `sites`) are left out:
     * they aren't readable as text.
     */
    val otherMeta: List<Pair<String, String>>
        get() = meta.entries
            .filter { (key, value) ->
                key.removePrefix("fedi:") !in SURFACED_META_KEYS && value.isNotBlank() && !looksStructured(value)
            }
            .sortedBy { it.key }
            .map { it.key to it.value.trim() }

    /** Fedi-run federations namespace their keys `fedi:`; prefer the standard key. */
    fun metaValue(key: String): String? =
        (meta[key] ?: meta["fedi:$key"])?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Modules a person would recognise, as product words: ecash, Lightning and
     * on-chain first, then anything else the federation runs. The `meta`
     * module is plumbing (its content shows up as the message, limits and
     * terms) and is left to the technical details.
     */
    val moduleLabels: List<String>
        get() = modules
            .filterNot { it.equals("meta", ignoreCase = true) }
            .sortedBy { kind -> MODULE_ORDER.indexOf(federationModuleLabel(kind)).let { if (it < 0) MODULE_ORDER.size else it } }
            .map(::federationModuleLabel)
            .distinct()

    private companion object {
        const val MSATS_PER_SAT = 1000L
        val SURFACED_META_KEYS = setOf(
            "federation_name", "federation_icon_url", "welcome_message", "preview_message",
            "federation_expiry_timestamp", "popup_end_timestamp", "popup_countdown_message", "popup_ended_message",
            "max_balance_msats", "max_invoice_msats", "tos_url", "invite_code", "meta_external_url", "meta_override_url",
        )

        fun looksStructured(value: String): Boolean {
            val trimmed = value.trim()
            return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))
        }
        val MODULE_ORDER = listOf("Ecash", "Lightning", "On-chain")
    }
}

data class FederationGuardian(
    val peerId: Int,
    val url: String,
    /** The name the guardian chose in the federation config; null when only the invite is known. */
    val name: String? = null,
    val health: GuardianHealth = GuardianHealth.Unknown,
) {
    /** The endpoint's host, keeping a non-default port: `wss://api.fed.example:8174/` → `api.fed.example:8174`. */
    val host: String
        get() = runCatching {
            val uri = URI(url)
            val host = uri.host ?: return@runCatching null
            if (uri.port == -1) host else "$host:${uri.port}"
        }.getOrNull() ?: url.substringAfter("://").trimEnd('/')

    /** The configured name, sentence-cased; `Guardian N` (1-based) when there is none. */
    val displayName: String
        get() = name?.trim()?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() } ?: "Guardian ${peerId + 1}"

    /** Avatar glyph: the number that ends `Guardian 3`, else the name's first letter. */
    val monogram: String
        get() = TRAILING_NUMBER.find(displayName)?.groupValues?.get(1) ?: displayName.first().uppercase()

    private companion object {
        val TRAILING_NUMBER = Regex("""(\d+)\s*$""")
    }
}

/**
 * A guardian as consensus sees it. Active: connected and signing. Behind:
 * reachable, but not contributing to recent sessions. Offline: no guardian
 * can reach it. Unknown: nobody answered.
 */
enum class GuardianHealth { Active, Behind, Offline, Unknown }

/** Names compared with their digit runs as numbers. */
private val GuardianNameOrder = Comparator<FederationGuardian> { a, b ->
    val chunk = Regex("""\d+|\D+""")
    val left = chunk.findAll(a.displayName.lowercase()).map { it.value }.toList()
    val right = chunk.findAll(b.displayName.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(left.size, right.size)) {
        val x = left[i]
        val y = right[i]
        val order = if (x[0].isDigit() && y[0].isDigit()) {
            compareValues(x.toBigInteger(), y.toBigInteger())
        } else {
            x.compareTo(y)
        }
        if (order != 0) return@Comparator order
    }
    compareValues(left.size, right.size).takeIf { it != 0 } ?: compareValues(a.peerId, b.peerId)
}

enum class FederationNetwork(val displayName: String) {
    Bitcoin("Bitcoin"),
    Testnet("Testnet"),
    Testnet4("Testnet4"),
    Signet("Signet"),
    Regtest("Regtest");

    val isMainnet: Boolean get() = this == Bitcoin
}

/** The SDK's lifecycle state for a stored federation. */
sealed interface FederationState {
    data object Running : FederationState
    /** Rebuilding the wallet from the seed; payments are refused until it finishes. */
    data object Recovering : FederationState
    /** Stored, but the SDK refused to open it; [reason] is the SDK's diagnostic. */
    data class Quarantined(val reason: String) : FederationState
    data object Closed : FederationState
}

/**
 * Fedimint's consensus is Byzantine fault tolerant: with n guardians it keeps
 * working while f = ⌊(n − 1) / 3⌋ of them are offline or misbehaving, and the
 * remaining n − f must agree to sign anything.
 */
data class FederationQuorum(val guardians: Int) {
    val tolerated: Int get() = ((guardians - 1) / 3).coerceAtLeast(0)
    val threshold: Int get() = guardians - tolerated
}

fun federationModuleLabel(kind: String): String = when (kind.lowercase()) {
    "mint", "mintv2" -> "Ecash"
    "ln", "lnv2" -> "Lightning"
    "wallet", "walletv2" -> "On-chain"
    "meta" -> "Metadata"
    else -> kind.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
}
