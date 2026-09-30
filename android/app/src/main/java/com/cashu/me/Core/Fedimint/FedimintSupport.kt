package com.cashu.me.Core.Fedimint

import com.cashu.me.Models.TokenInfo

/**
 * Fedimint support seam shared by every flavor.
 *
 * The Fedimint SDK only ships in the `fedimint` flavor, so main code never
 * touches it. Instead the flavor's `FedimintGatewayFactory` installs
 * [notesParser] and routes federation work through a gateway; in the stock
 * `cashu` flavor nothing is installed and every helper below reports "not
 * Fedimint".
 *
 * A federation is tracked as a mint whose `url` is `fedimint:<federationId>`.
 */
object FedimintSupport {
    const val KEY_PREFIX = "fedimint:"
    const val MINT_QUOTE_PREFIX = "fm-mint-"
    const val MELT_QUOTE_PREFIX = "fm-melt-"

    /** Set by the fedimint flavor; parses OOB notes into a sat value, or null when they are not notes. */
    @Volatile
    var notesParser: ((String) -> Long?)? = null

    val isAvailable: Boolean get() = notesParser != null

    private val NOT_NOTES_PREFIXES = listOf("cashu", "creq", "lnbc", "lntb", "lno1", "lnurl", "bitcoin:", "http", "nostr:", "npub1", "nsec1")

    fun isFederationKey(mintUrl: String?): Boolean = mintUrl?.startsWith(KEY_PREFIX) == true

    fun federationId(key: String): String = key.removePrefix(KEY_PREFIX)

    fun keyFor(federationId: String): String = KEY_PREFIX + federationId

    fun isFederationQuoteId(id: String): Boolean =
        id.startsWith(MINT_QUOTE_PREFIX) || id.startsWith(MELT_QUOTE_PREFIX)

    /** Invite codes are bech32 strings starting `fed1`; tolerate a `fedimint:` scheme and stray whitespace. */
    fun extractInvite(raw: String): String? {
        val candidate = raw.trim().trim('"', '\'').removePrefix("fedimint://").removePrefix("fedimint:")
        return candidate.takeIf {
            it.length > 20 && it.startsWith("fed1", ignoreCase = true) && it.none(Char::isWhitespace)
        }
    }

    /**
     * Fedimint OOB notes for a joined federation, as a bearer string. Only
     * meaningful in the fedimint flavor: the SDK is asked to parse it.
     */
    fun extractNotes(raw: String): String? {
        val parser = notesParser ?: return null
        val candidate = raw.trim().trim('"', '\'').removePrefix("fedimint://").removePrefix("fedimint:")
        if (candidate.length < 20 || candidate.any(Char::isWhitespace)) return null
        if (NOT_NOTES_PREFIXES.any { candidate.startsWith(it, ignoreCase = true) }) return null
        if (extractInvite(candidate) != null) return null
        return candidate.takeIf { parser(it) != null }
    }

    fun tokenInfo(raw: String): TokenInfo? {
        val notes = extractNotes(raw) ?: return null
        val sats = notesParser?.invoke(notes) ?: return null
        return TokenInfo(
            amount = sats,
            mint = "Fedimint",
            unit = "sat",
            memo = null,
            proofCount = 0,
        )
    }
}
