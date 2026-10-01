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
    /** Every module kind the federation runs (`mint`, `ln`, `wallet`, `meta`, …). */
    val modules: List<String> = emptyList(),
    /** Merged metadata: consensus values override configuration values per key. */
    val meta: Map<String, String> = emptyMap(),
    val metaRevision: Long? = null,
) {
    val quorum: FederationQuorum? get() = guardianCount?.takeIf { it > 0 }?.let(::FederationQuorum)

    val welcomeMessage: String? get() = metaValue("welcome_message")
    val iconUrl: String? get() = metaValue("federation_icon_url")
    val tosUrl: String? get() = metaValue("tos_url")
    val successorInvite: String? get() = metaValue("federation_successor")
    val expiresAtEpochSeconds: Long? get() = metaValue("federation_expiry_timestamp")?.toLongOrNull()
    val maxBalanceSats: Long? get() = metaValue("max_balance_msats")?.toLongOrNull()?.div(MSATS_PER_SAT)
    val maxInvoiceSats: Long? get() = metaValue("max_invoice_msats")?.toLongOrNull()?.div(MSATS_PER_SAT)

    /** A guardian announcement with a countdown, while it is still running. */
    fun activePopup(nowEpochSeconds: Long): String? {
        val end = metaValue("popup_end_timestamp")?.toLongOrNull() ?: return null
        if (end <= nowEpochSeconds) return null
        return metaValue("popup_countdown_message")
    }

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
        val MODULE_ORDER = listOf("Ecash", "Lightning", "On-chain")
    }
}

data class FederationGuardian(val peerId: Int, val url: String) {
    /** The endpoint's host, keeping a non-default port: `wss://api.fed.example:8174/` → `api.fed.example:8174`. */
    val host: String
        get() = runCatching {
            val uri = URI(url)
            val host = uri.host ?: return@runCatching null
            if (uri.port == -1) host else "$host:${uri.port}"
        }.getOrNull() ?: url.substringAfter("://").trimEnd('/')
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
