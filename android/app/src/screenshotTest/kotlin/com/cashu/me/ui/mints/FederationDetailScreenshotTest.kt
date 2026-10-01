package com.cashu.me.ui.mints

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.cashu.me.Core.AmountDisplayPrimary
import com.cashu.me.Core.AmountDisplayText
import com.cashu.me.Core.AmountParts
import com.cashu.me.Models.FederationDetails
import com.cashu.me.Models.FederationGuardian
import com.cashu.me.Models.FederationNetwork
import com.cashu.me.Models.FederationState
import com.cashu.me.Models.GuardianHealth
import com.cashu.me.Models.MintInfo
import com.cashu.me.ui.theme.CashuTheme
import java.time.ZoneOffset

private const val BitcoinPrinciplesId = "b21068c84f5b12ca4fdf93f3e443d3bd7c27e8642d0d52ea2e4dce6fdbbee9df"

private val previewMint = MintInfo(url = "fedimint:$BitcoinPrinciplesId", name = "Bitcoin Principles", balance = 21_000)

private val previewBalance = AmountDisplayText(
    primary = "₿21,000",
    secondary = "$13.20",
    effectivePrimary = AmountDisplayPrimary.Sats,
    primaryParts = AmountParts("21,000", AmountParts.Affix.Prefix("₿")),
)

private const val WelcomeMessage = "Welcome to the Bitcoin Principles Federation! \n" +
    "This Federation is scheduled to end at the specified date. \n" +
    "See the federation's Terms of Service for full details. \n" +
    "Max transaction amount: 200,000 sats"

// Shaped like Bitcoin Principles' real config + external meta (Fedi modules, JSON-valued keys included).
private val previewDetails = FederationDetails(
    federationId = BitcoinPrinciplesId,
    name = "Bitcoin Principles",
    network = FederationNetwork.Bitcoin,
    state = FederationState.Running,
    supportsEcash = true,
    supportsLightning = true,
    supportsOnchain = true,
    inviteCode = "fed11qgqzygrhwden5te0v9cxjtnzd96xxmmfdec8y6twvd5hqmr9wvhxuet59upqzg9jzp5vsn6mzt9ylhun70jy85aa0sn7sepdp4fw5tjdeehah0hfmufvlqem",
    inviteGuardians = listOf(FederationGuardian(2, "wss://api.bitcoinprinciples.net/")),
    guardianCount = 4,
    guardianRoster = listOf(
        FederationGuardian(0, "wss://api.bitcoin-principles.com/", name = "Guardian 1", health = GuardianHealth.Active),
        FederationGuardian(1, "wss://api.bitcoinprinciples.xyz/", name = "Guardian 4", health = GuardianHealth.Active),
        FederationGuardian(2, "wss://api.bitcoinprinciples.net/", name = "Guardian 3", health = GuardianHealth.Active),
        FederationGuardian(3, "wss://api.bitcoinprinciples.dev/", name = "guardian 2", health = GuardianHealth.Active),
    ),
    sessionCount = 445_145,
    modules = listOf("ln", "mint", "wallet", "meta", "stability_pool", "fedi-social"),
    meta = mapOf(
        "federation_name" to "Bitcoin Principles",
        "welcome_message" to WelcomeMessage,
        "preview_message" to WelcomeMessage,
        "popup_end_timestamp" to "1806969599",
        "popup_countdown_message" to "This community will end at the specified date. All unredeemed eCash will expire at that time, and the remaining funds will be managed at the discretion of the guardians.",
        "max_balance_msats" to "1000000000",
        "max_invoice_msats" to "200000000",
        "tos_url" to "https://btc-principles.replit.app",
        "default_currency" to "USD",
        "public" to "true",
        "sites" to """[{"id":"bitcoin-principles","url":"https://bitcoinprinciples.org"}]""",
        "vetted_gateways" to """["03ab"]""",
    ),
)

@Composable
private fun FederationPreview(
    details: FederationDetails?,
    connection: MintConnectionState,
    active: Boolean = true,
    now: Long = 1_790_000_000,
    technicalExpanded: Boolean = false,
) {
    CashuTheme {
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FederationDetailBody(
                    mint = previewMint.copy(name = details?.name ?: previewMint.name),
                    isActive = active,
                    details = details,
                    connection = connection,
                    onRetry = {},
                    balance = previewBalance,
                    useBitcoinSymbol = true,
                    onShowInvite = {},
                    nowEpochSeconds = now,
                    zone = ZoneOffset.UTC,
                    technicalExpanded = technicalExpanded,
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "federation-live-dark", widthDp = 390, heightDp = 1900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "federation-live-light", widthDp = 390, heightDp = 1900)
@Composable
fun federationDetailLive() = FederationPreview(previewDetails, MintConnectionState.Online)

@PreviewTest
@Preview(name = "federation-technical-dark", widthDp = 390, heightDp = 2300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailTechnical() = FederationPreview(previewDetails, MintConnectionState.Online, technicalExpanded = true)

@PreviewTest
@Preview(name = "federation-degraded-dark", widthDp = 390, heightDp = 1900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailDegraded() = FederationPreview(
    previewDetails.copy(
        guardianRoster = previewDetails.guardianRoster.mapIndexed { index, guardian ->
            guardian.copy(health = listOf(GuardianHealth.Active, GuardianHealth.Offline, GuardianHealth.Behind, GuardianHealth.Active)[index])
        },
    ),
    MintConnectionState.Online,
)

@PreviewTest
@Preview(name = "federation-checking-dark", widthDp = 390, heightDp = 1500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailChecking() = FederationPreview(
    previewDetails.copy(
        network = FederationNetwork.Signet,
        guardianCount = null,
        modules = emptyList(),
        meta = emptyMap(),
        sessionCount = null,
        guardianRoster = previewDetails.guardianRoster.map { it.copy(health = GuardianHealth.Unknown) },
    ),
    MintConnectionState.Checking,
)

@PreviewTest
@Preview(name = "federation-offline-dark", widthDp = 390, heightDp = 1300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailOffline() = FederationPreview(
    previewDetails.copy(guardianCount = null, modules = emptyList(), meta = emptyMap(), sessionCount = null, guardianRoster = emptyList()),
    MintConnectionState.Offline,
    active = false,
)

@PreviewTest
@Preview(name = "federation-ended-dark", widthDp = 390, heightDp = 1900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailEnded() = FederationPreview(previewDetails, MintConnectionState.Online, now = 1_807_000_000)

@PreviewTest
@Preview(name = "federation-quarantined-dark", widthDp = 390, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailQuarantined() = FederationPreview(
    FederationDetails(
        federationId = BitcoinPrinciplesId,
        name = "Bitcoin Principles",
        network = FederationNetwork.Bitcoin,
        state = FederationState.Quarantined("The federation's storage is in use by another process."),
        supportsEcash = false,
        supportsLightning = false,
        supportsOnchain = false,
        inviteCode = "",
        inviteGuardians = emptyList(),
    ),
    MintConnectionState.Offline,
    active = false,
)

@PreviewTest
@Preview(name = "federation-single-guardian-dark", widthDp = 390, heightDp = 1300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailSingleGuardian() = FederationPreview(
    previewDetails.copy(
        name = "Solo Mint",
        guardianCount = 1,
        modules = listOf("mint", "ln", "wallet"),
        meta = emptyMap(),
        inviteGuardians = listOf(FederationGuardian(0, "wss://solo.example.com/")),
        guardianRoster = listOf(FederationGuardian(0, "wss://solo.example.com/", name = "Solo", health = GuardianHealth.Active)),
    ),
    MintConnectionState.Online,
    active = false,
)

@PreviewTest
@Preview(name = "federation-large-text", widthDp = 320, heightDp = 3800, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailLargeText() = FederationPreview(previewDetails, MintConnectionState.Online)
