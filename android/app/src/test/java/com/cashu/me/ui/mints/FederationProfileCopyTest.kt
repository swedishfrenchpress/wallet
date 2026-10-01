package com.cashu.me.ui.mints

import com.cashu.me.Core.AmountFormatter
import com.cashu.me.Models.FederationGuardian
import com.cashu.me.Models.FederationState
import com.cashu.me.Models.GuardianHealth
import com.cashu.me.Models.federationDetails
import com.cashu.me.ui.components.NoticeSeverity
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FederationProfileCopyTest {
    // Bitcoin Principles' countdown: 1806969599 is 2027-04-05T23:59:59Z.
    private val bitcoinPrinciples = mapOf(
        "popup_end_timestamp" to "1806969599",
        "popup_countdown_message" to "This community will end at the specified date.",
    )

    private fun lifecycle(
        meta: Map<String, String> = emptyMap(),
        state: FederationState = FederationState.Running,
        now: Long = 1_790_000_000,
    ) = federationLifecycle(federationDetails(meta = meta, state = state), now, ZoneOffset.UTC, Locale.US)

    @Test
    fun lifecycleShowsTheEndDateInTheHeadline() {
        assertEquals(
            FederationLifecycle(NoticeSeverity.Caution, "Ends Apr 5, 2027", "This community will end at the specified date."),
            lifecycle(bitcoinPrinciples),
        )
    }

    @Test
    fun lifecycleAfterTheEndIsAnError() {
        assertEquals(
            FederationLifecycle(NoticeSeverity.Error, "Ended Apr 5, 2027", null),
            lifecycle(bitcoinPrinciples, now = 1_806_969_600),
        )
        assertEquals(
            FederationLifecycle(NoticeSeverity.Error, "Ended Apr 5, 2027", "Funds were returned."),
            lifecycle(bitcoinPrinciples + ("popup_ended_message" to "Funds were returned."), now = 1_806_969_600),
        )
    }

    @Test
    fun lifecycleExpiryBeatsThePopupEndAndHasAFallbackMessage() {
        val expiry = mapOf("federation_expiry_timestamp" to "1798761600") // 2027-01-01T00:00:00Z
        assertEquals(
            FederationLifecycle(NoticeSeverity.Caution, "Ends Jan 1, 2027", "This community will end at the specified date."),
            lifecycle(bitcoinPrinciples + expiry),
        )
        assertEquals(
            FederationLifecycle(NoticeSeverity.Caution, "Ends Jan 1, 2027", "Move your funds out before then."),
            lifecycle(expiry),
        )
    }

    @Test
    fun lifecycleCantRunStatesComeFirstAndRecoveryLast() {
        assertEquals(
            FederationLifecycle(NoticeSeverity.Error, "Couldn't open this federation", "Storage is locked"),
            lifecycle(bitcoinPrinciples, FederationState.Quarantined("Storage is locked")),
        )
        assertEquals(
            FederationLifecycle(NoticeSeverity.Error, "Couldn't open this federation", null),
            lifecycle(state = FederationState.Quarantined(" ")),
        )
        assertEquals(FederationLifecycle(NoticeSeverity.Error, "Closed in this wallet", null), lifecycle(state = FederationState.Closed))
        assertEquals(
            FederationLifecycle(NoticeSeverity.Caution, "Restoring your balance", "Payments resume when it's done."),
            lifecycle(state = FederationState.Recovering),
        )
        assertEquals("Ends Apr 5, 2027", lifecycle(bitcoinPrinciples, FederationState.Recovering)?.headline)
        assertNull(lifecycle())
    }

    @Test
    fun guardianStatusIsTheScreensOnlyConnectionStatus() {
        val roster = (0..3).map { FederationGuardian(it, "wss://g$it/", health = GuardianHealth.Active) }
        val healthy = federationDetails(guardianCount = 4, roster = roster)
        val countOnly = federationDetails(guardianCount = 4)

        assertEquals(GuardianStatusCopy("All 4 online"), guardianStatusCopy(healthy, MintConnectionState.Online))
        assertEquals(GuardianStatusCopy("Checking guardians…", busy = true), guardianStatusCopy(countOnly, MintConnectionState.Checking))
        assertEquals(GuardianStatusCopy("Checking guardians…", busy = true), guardianStatusCopy(null, MintConnectionState.Checking))
        assertEquals(
            GuardianStatusCopy("Couldn't reach the guardians", showsRetry = true),
            guardianStatusCopy(healthy, MintConnectionState.Offline),
        )
        assertEquals(
            GuardianStatusCopy("4 guardians", "Couldn't check who's online"),
            guardianStatusCopy(countOnly, MintConnectionState.Online),
        )
        assertEquals(GuardianStatusCopy("4 guardians"), guardianStatusCopy(countOnly, MintConnectionState.NotChecked))
        assertEquals(GuardianStatusCopy("Guardians"), guardianStatusCopy(null, MintConnectionState.NotChecked))
    }

    @Test
    fun aFederationTheSdkWontRunHasNothingToRetry() {
        listOf(FederationState.Quarantined("Storage is locked"), FederationState.Closed).forEach { state ->
            val status = guardianStatusCopy(federationDetails(guardianCount = 4, state = state), MintConnectionState.Offline)
            assertEquals(GuardianStatusCopy("4 guardians"), status)
            assertFalse(status.showsRetry)
        }
    }

    @Test
    fun paymentRowsUseProductWordsAndGroupedLimits() {
        val meta = mapOf("max_balance_msats" to "1000000000", "max_invoice_msats" to "200000000")
        val formatter = AmountFormatter(Locale.US)

        assertEquals(
            listOf(
                DetailRowCopy("Supports", "Lightning · Ecash"),
                DetailRowCopy("Max balance", "1,000,000 sat", digits = true),
                DetailRowCopy("Max payment", "200,000 sat", digits = true),
            ),
            federationPaymentRows(federationDetails(meta = meta), formatter, useBitcoinSymbol = false),
        )
        assertEquals(
            "₿1,000,000",
            federationPaymentRows(federationDetails(meta = meta), formatter, useBitcoinSymbol = true)[1].value,
        )
        assertEquals(
            listOf(DetailRowCopy("Supports", "Ecash")),
            federationPaymentRows(federationDetails(lightning = false), formatter, useBitcoinSymbol = false),
        )
        assertEquals(
            emptyList<DetailRowCopy>(),
            federationPaymentRows(federationDetails(lightning = false, ecash = false), formatter, useBitcoinSymbol = false),
        )
    }

    @Test
    fun technicalEntriesAreCuratedThenRawScalarMeta() {
        val details = federationDetails(
            guardianCount = 4,
            modules = listOf("ln", "mint", "wallet", "meta", "stability_pool", "fedi-social"),
            meta = mapOf("public" to "true", "sites" to """[{"id":"x"}]""", "welcome_message" to "Hi", "default_currency" to "USD"),
            sessionCount = 504_328,
            metaRevision = 3,
        )

        assertEquals(
            listOf(
                TechnicalEntryCopy("Modules", "ln, mint, wallet, meta, stability_pool, fedi-social"),
                TechnicalEntryCopy("Signing threshold", "3 of 4"),
                TechnicalEntryCopy("Consensus session", "504,328"),
                TechnicalEntryCopy("Metadata revision", "3"),
                TechnicalEntryCopy("default_currency", "USD", monoLabel = true),
                TechnicalEntryCopy("public", "true", monoLabel = true),
            ),
            federationTechnicalEntries(details, Locale.US),
        )
        assertEquals(
            "a single guardian has no threshold worth listing",
            emptyList<TechnicalEntryCopy>(),
            federationTechnicalEntries(federationDetails(guardianCount = 1), Locale.US),
        )
    }
}
