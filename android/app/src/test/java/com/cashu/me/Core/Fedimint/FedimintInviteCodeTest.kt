package com.cashu.me.Core.Fedimint

import com.cashu.me.Models.FederationGuardian
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FedimintInviteCodeTest {
    @Test
    fun decodesGuardianEndpointAndFederationIdFromRealInvite() {
        // Mutinynet test federation, as published in the fedimint-sdk examples.
        val decoded = FedimintInviteCode.decode(MUTINYNET_INVITE)!!

        assertEquals("15db8cb4f1ec8e484d73b889372bec94812580f929e8148b7437d359af422cd3", decoded.federationId)
        assertEquals(
            listOf(FederationGuardian(peerId = 0, url = "wss://alpha.mutinynet-05-alephbft.dev.fedibtc.com/")),
            decoded.guardians,
        )
        assertEquals("alpha.mutinynet-05-alephbft.dev.fedibtc.com", decoded.guardians.single().host)
    }

    @Test
    fun decodesSecondInviteShape() {
        val decoded = FedimintInviteCode.decode(WNEXT_INVITE)!!

        assertEquals("9f98abdce9d19c678b48f1dc89cb52b76d4143691e5b23ca90f1b9e2685226fb", decoded.federationId)
        assertEquals("wss://api-unlawful-orc-r5yiydhe2a3ywzo5iiyc.wnext.app/", decoded.guardians.single().url)
    }

    @Test
    fun toleratesSurroundingWhitespaceAndUppercase() {
        assertEquals(
            FedimintInviteCode.decode(MUTINYNET_INVITE),
            FedimintInviteCode.decode("  ${MUTINYNET_INVITE.uppercase()}\n"),
        )
    }

    @Test
    fun rejectsCorruptedChecksum() {
        val flipped = MUTINYNET_INVITE.dropLast(1) + if (MUTINYNET_INVITE.last() == 'q') 'p' else 'q'
        assertNull(FedimintInviteCode.decode(flipped))
    }

    @Test
    fun rejectsNonInviteStrings() {
        assertNull(FedimintInviteCode.decode(""))
        assertNull(FedimintInviteCode.decode("https://mint.example.com"))
        assertNull(FedimintInviteCode.decode("fed1"))
    }

    @Test
    fun keepsNonDefaultPortInHost() {
        assertEquals("guardian.example:8174", FederationGuardian(1, "wss://guardian.example:8174/").host)
        assertEquals("guardian.example", FederationGuardian(1, "wss://guardian.example/ws").host)
    }

    private companion object {
        const val MUTINYNET_INVITE =
            "fed11qgqrgvnhwden5te0v9k8q6rp9ekh2arfdeukuet595cr2ttpd3jhq6rzve6zuer9wchxvetyd938gcewvdhk6tcqqysptkuvknc7erjgf4em3zfh90kffqf9srujn6q53d6r056e4apze5cw27h75"
        const val WNEXT_INVITE =
            "fed11qgqrsdnhwden5te0v9cxjtt4dekxzamxw4kz6mmjvvkhydted9ukg6r9xfsnx7th0fhn26tf093juamwv4u8gtnpwpcz7qqpyz0e327ua8geceutfrcaezwt22mk6s2rdy09kg72jrcmncng2gn0kp2m5sk"
    }
}
