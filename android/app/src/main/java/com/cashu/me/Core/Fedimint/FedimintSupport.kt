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

    /** Turns a reassembled animated-QR message back into a notes string the SDK accepts. */
    fun notesFromFountainMessage(message: ByteArray): String? {
        val parser = notesParser ?: return null
        return FedimintFountainDecoder.notesCandidates(message).firstOrNull { parser(it) != null }
    }

    /** Stand-in `TokenInfo.mint` until the notes are matched to a joined federation. */
    const val NOTES_MINT_PLACEHOLDER = "Fedimint"

    /**
     * The 4-byte federation-id prefix embedded in v1 OOB notes, as 8 hex chars, or null
     * when the notes don't carry one in a shape we recognise.
     */
    /** The consensus-encoded bytes behind a notes string (`fedimint…` base32 or base64). */
    fun notesBytes(raw: String): ByteArray? = runCatching {
        val candidate = raw.trim().removePrefix("fedimint://").removePrefix("fedimint:")
        if (candidate.startsWith(FedimintFountainDecoder.PREFIX, ignoreCase = true)) {
            FedimintFountainDecoder.base32Decode(candidate.lowercase().removePrefix(FedimintFountainDecoder.PREFIX))
        } else {
            val normalised = candidate.replace('-', '+').replace('_', '/').trimEnd('=')
            java.util.Base64.getDecoder().decode(normalised + "=".repeat((4 - normalised.length % 4) % 4))
        }
    }.getOrNull()

    fun notesFederationPrefix(raw: String): String? = runCatching {
        val bytes = notesBytes(raw) ?: return null
        var pos = 0
        fun varInt(): Long {
            val first = bytes[pos++].toInt() and 0xFF
            val width = when (first) { 0xFD -> 2; 0xFE -> 4; 0xFF -> 8; else -> 0 }
            if (width == 0) return first.toLong()
            var value = 0L
            repeat(width) { value = (value shl 8) or (bytes[pos++].toLong() and 0xFF) }
            return value
        }
        val parts = varInt()
        var prefix: String? = null
        for (i in 0 until parts) {
            val variant = varInt()
            val length = varInt().toInt()
            if (variant == 1L && length == 4) {
                prefix = bytes.copyOfRange(pos, pos + 4).joinToString("") { "%02x".format(it) }
            }
            pos += length
        }
        prefix
    }.getOrNull()

    /**
     * The joined federation these notes belong to: matched by id prefix when the notes
     * carry one, otherwise the only joined federation, otherwise null.
     */
    fun federationKeyForNotes(raw: String, mints: List<com.cashu.me.Models.MintInfo>): String? {
        val federations = mints.map { it.url }.filter(::isFederationKey)
        notesFederationPrefix(raw)?.let { prefix ->
            federations.firstOrNull { federationId(it).startsWith(prefix, ignoreCase = true) }?.let { return it }
        }
        return federations.singleOrNull()
    }

    fun tokenInfo(raw: String): TokenInfo? {
        val notes = extractNotes(raw) ?: return null
        val sats = notesParser?.invoke(notes) ?: return null
        return TokenInfo(
            amount = sats,
            mint = NOTES_MINT_PLACEHOLDER,
            unit = "sat",
            memo = null,
            proofCount = 0,
        )
    }
}
