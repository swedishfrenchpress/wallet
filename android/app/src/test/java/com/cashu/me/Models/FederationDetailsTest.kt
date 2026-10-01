package com.cashu.me.Models

import com.cashu.me.ui.mints.federationSubtitle
import com.cashu.me.ui.mints.quorumSubtitle
import com.cashu.me.ui.mints.quorumTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FederationDetailsTest {
    @Test
    fun quorumFollowsByzantineFaultTolerance() {
        // n guardians tolerate ⌊(n − 1) / 3⌋ faults; the rest must agree.
        val expected = mapOf(1 to (1 to 0), 2 to (2 to 0), 3 to (3 to 0), 4 to (3 to 1), 5 to (4 to 1), 7 to (5 to 2), 10 to (7 to 3))
        expected.forEach { (guardians, pair) ->
            val quorum = FederationQuorum(guardians)
            assertEquals("threshold for $guardians", pair.first, quorum.threshold)
            assertEquals("tolerated for $guardians", pair.second, quorum.tolerated)
        }
    }

    @Test
    fun quorumIsUnknownUntilTheGuardianCountArrives() {
        assertNull(details().quorum)
        assertNull(details(guardianCount = 0).quorum)
        assertEquals(FederationQuorum(4), details(guardianCount = 4).quorum)
    }

    @Test
    fun metaPrefersStandardKeysAndFallsBackToFediNamespace() {
        val meta = mapOf(
            "welcome_message" to "  Hello  ",
            "fedi:welcome_message" to "Ignored",
            "fedi:tos_url" to "https://fed.example/tos",
            "fedi:max_balance_msats" to "1000000000",
            "max_invoice_msats" to "250000999",
            "federation_icon_url" to "",
        )
        val details = details(meta = meta)

        assertEquals("Hello", details.welcomeMessage)
        assertEquals("https://fed.example/tos", details.tosUrl)
        assertEquals(1_000_000L, details.maxBalanceSats)
        assertEquals(250_000L, details.maxInvoiceSats)
        assertNull("blank values are absent", details.iconUrl)
    }

    @Test
    fun popupShowsOnlyWhileItsCountdownRuns() {
        val details = details(meta = mapOf("popup_end_timestamp" to "2000", "popup_countdown_message" to "Migrating soon"))

        assertEquals("Migrating soon", details.activePopup(nowEpochSeconds = 1999))
        assertNull(details.activePopup(nowEpochSeconds = 2000))
    }

    @Test
    fun expiryParsesSeconds() {
        assertEquals(1_900_000_000L, details(meta = mapOf("federation_expiry_timestamp" to "1900000000")).expiresAtEpochSeconds)
        assertNull(details(meta = mapOf("federation_expiry_timestamp" to "soon")).expiresAtEpochSeconds)
    }

    @Test
    fun moduleLabelsMergeGenerationsOrderAndHideMeta() {
        val details = details(modules = listOf("fedi-social", "ln", "lnv2", "stability_pool", "meta", "wallet", "mint"))

        assertEquals(listOf("Ecash", "Lightning", "On-chain", "Fedi social", "Stability pool"), details.moduleLabels)
    }

    @Test
    fun copyDescribesTheQuorum() {
        assertEquals("Single guardian" to "No backup if it goes offline", quorumTitle(FederationQuorum(1)) to quorumSubtitle(FederationQuorum(1)))
        assertEquals("All 3 must agree" to "Stops if any goes offline", quorumTitle(FederationQuorum(3)) to quorumSubtitle(FederationQuorum(3)))
        assertEquals("3 of 4 must agree" to "Keeps working with 1 offline", quorumTitle(FederationQuorum(4)) to quorumSubtitle(FederationQuorum(4)))
        assertEquals("5 of 7 must agree" to "Keeps working with up to 2 offline", quorumTitle(FederationQuorum(7)) to quorumSubtitle(FederationQuorum(7)))
        assertEquals("Fedimint federation", federationSubtitle(null))
        assertEquals("Fedimint federation · 1 guardian", federationSubtitle(1))
        assertEquals("Fedimint federation · 4 guardians", federationSubtitle(4))
    }

    private fun details(
        guardianCount: Int? = null,
        modules: List<String> = emptyList(),
        meta: Map<String, String> = emptyMap(),
    ) = FederationDetails(
        federationId = "15db8cb4f1ec8e484d73b889372bec94812580f929e8148b7437d359af422cd3",
        name = "Mutinynet",
        network = FederationNetwork.Signet,
        state = FederationState.Running,
        supportsEcash = true,
        supportsLightning = true,
        supportsOnchain = true,
        inviteCode = "fed11…",
        inviteGuardians = emptyList(),
        guardianCount = guardianCount,
        modules = modules,
        meta = meta,
    )
}
