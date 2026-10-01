package com.cashu.me.Models

import com.cashu.me.ui.mints.federationSubtitle
import com.cashu.me.ui.mints.GuardianHealthCopy
import com.cashu.me.ui.mints.guardianHealthCopy
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
    fun headerSubtitleCountsGuardians() {
        assertEquals("Fedimint federation", federationSubtitle(null))
        assertEquals("Fedimint federation · 1 guardian", federationSubtitle(1))
        assertEquals("Fedimint federation · 4 guardians", federationSubtitle(4))
    }

    @Test
    fun healthCopySaysHowManyAreOnlineAndWhetherPaymentsWork() {
        fun copy(vararg health: GuardianHealth) = guardianHealthCopy(
            details(roster = health.mapIndexed { i, h -> FederationGuardian(i, "wss://g$i/", health = h) }),
        )
        val a = GuardianHealth.Active
        val b = GuardianHealth.Behind
        val o = GuardianHealth.Offline

        assertEquals(GuardianHealthCopy("All 4 online", "Signing normally", false), copy(a, a, a, a))
        assertEquals(GuardianHealthCopy("3 of 4 online", "Payments still work", false), copy(a, a, a, o))
        assertEquals(GuardianHealthCopy("All 4 online", "1 behind, payments still work", false), copy(a, a, a, b))
        assertEquals(GuardianHealthCopy("2 of 4 online", "Too few signing to make payments", true), copy(a, a, o, o))
        assertEquals(GuardianHealthCopy("Guardian online", "Single guardian, no backup", false), copy(a))
        assertEquals(GuardianHealthCopy("Guardian offline", "Payments are paused", true), copy(o))
        assertNull("unknown health has no copy", copy(GuardianHealth.Unknown, GuardianHealth.Unknown))
    }

    @Test
    fun guardiansReadByNameInNaturalOrder() {
        // Bitcoin Principles' config: peer ids don't follow the names.
        val roster = listOf(
            FederationGuardian(0, "wss://a/", name = "Guardian 1"),
            FederationGuardian(1, "wss://b/", name = "Guardian 4"),
            FederationGuardian(2, "wss://c/", name = "Guardian 3"),
            FederationGuardian(3, "wss://d/", name = "guardian 2"),
            FederationGuardian(4, "wss://e/", name = "Guardian 10"),
        )

        val ordered = details(roster = roster).guardians

        assertEquals(listOf("Guardian 1", "Guardian 2", "Guardian 3", "Guardian 4", "Guardian 10"), ordered.map { it.displayName })
        assertEquals(listOf("1", "2", "3", "4", "10"), ordered.map { it.monogram })
        assertEquals("A", FederationGuardian(0, "wss://a/", name = "alpha").monogram)
        assertEquals("Guardian 3", FederationGuardian(2, "wss://c/").displayName)
    }

    @Test
    fun rosterFallsBackToTheInviteAndCountsHealth() {
        val invite = listOf(FederationGuardian(0, "wss://a/"))
        assertEquals(invite, details(invite = invite).guardians)

        val roster = listOf(
            FederationGuardian(0, "wss://a/", health = GuardianHealth.Active),
            FederationGuardian(1, "wss://b/", health = GuardianHealth.Behind),
            FederationGuardian(2, "wss://c/", health = GuardianHealth.Offline),
        )
        val probed = details(roster = roster, invite = invite)
        assertEquals(3, probed.guardians.size)
        assertEquals(2, probed.onlineCount)
        assertEquals(1, probed.activeCount)
        assertEquals(FederationQuorum(3), probed.quorum)
    }

    private fun details(
        guardianCount: Int? = null,
        modules: List<String> = emptyList(),
        meta: Map<String, String> = emptyMap(),
        roster: List<FederationGuardian> = emptyList(),
        invite: List<FederationGuardian> = emptyList(),
    ) = FederationDetails(
        federationId = "15db8cb4f1ec8e484d73b889372bec94812580f929e8148b7437d359af422cd3",
        name = "Mutinynet",
        network = FederationNetwork.Signet,
        state = FederationState.Running,
        supportsEcash = true,
        supportsLightning = true,
        supportsOnchain = true,
        inviteCode = "fed11…",
        inviteGuardians = invite,
        guardianCount = guardianCount,
        guardianRoster = roster,
        modules = modules,
        meta = meta,
    )
}
