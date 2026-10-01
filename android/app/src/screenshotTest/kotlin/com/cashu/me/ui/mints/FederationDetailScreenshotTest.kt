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
import com.cashu.me.Models.FederationDetails
import com.cashu.me.Models.FederationGuardian
import com.cashu.me.Models.FederationNetwork
import com.cashu.me.Models.FederationState
import com.cashu.me.Models.MintInfo
import com.cashu.me.ui.theme.CashuTheme

private val previewMint = MintInfo(url = "fedimint:15db8cb4f1ec8e484d73b889372bec94812580f929e8148b7437d359af422cd3", name = "Mutinynet Signet", balance = 21_000)

private val previewDetails = FederationDetails(
    federationId = "15db8cb4f1ec8e484d73b889372bec94812580f929e8148b7437d359af422cd3",
    name = "Mutinynet Signet",
    network = FederationNetwork.Signet,
    state = FederationState.Running,
    supportsEcash = true,
    supportsLightning = true,
    supportsOnchain = true,
    inviteCode = "fed11qgqrgvnhwden5te0v9k8q6rp9ekh2arfdeukuet595cr2ttpd3jhq6rzve6zuer9wchxvetyd938gcewvdhk6tcqqysptkuvknc7erjgf4em3zfh90kffqf9srujn6q53d6r056e4apze5cw27h75",
    inviteGuardians = listOf(FederationGuardian(0, "wss://alpha.mutinynet-05-alephbft.dev.fedibtc.com/")),
    guardianCount = 4,
    modules = listOf("fedi-social", "ln", "mint", "stability_pool", "wallet", "meta"),
    meta = mapOf(
        "federation_name" to "Mutinynet Signet",
        "welcome_message" to "Welcome to the Mutinynet test federation. Coins here have no value.",
        "federation_expiry_timestamp" to "1924992000",
        "max_balance_msats" to "100000000000",
        "tos_url" to "https://fedi.xyz/terms",
    ),
    metaRevision = 3,
)

@Composable
private fun FederationPreview(details: FederationDetails?, connection: MintConnectionState, recovery: Boolean = false, active: Boolean = true) {
    CashuTheme {
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FederationDetailBody(
                    mint = previewMint.copy(name = details?.name ?: previewMint.name),
                    isActive = active,
                    details = details,
                    connection = connection,
                    showsRecovery = recovery,
                    onRetry = {},
                    balanceSecondary = "$13.20",
                    onShowInvite = {},
                    nowEpochSeconds = 1_790_000_000,
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "federation-live-dark", widthDp = 390, heightDp = 1500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "federation-live-light", widthDp = 390, heightDp = 1500)
@Composable
fun federationDetailLive() = FederationPreview(previewDetails, MintConnectionState.Online)

@PreviewTest
@Preview(name = "federation-checking-dark", widthDp = 390, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailChecking() = FederationPreview(
    previewDetails.copy(guardianCount = null, modules = emptyList(), meta = emptyMap(), metaRevision = null),
    MintConnectionState.Checking,
)

@PreviewTest
@Preview(name = "federation-offline-dark", widthDp = 390, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailOffline() = FederationPreview(
    previewDetails.copy(guardianCount = null, modules = emptyList(), meta = emptyMap(), metaRevision = null),
    MintConnectionState.Offline,
    recovery = true,
    active = false,
)

@PreviewTest
@Preview(name = "federation-single-guardian-dark", widthDp = 390, heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailSingleGuardian() = FederationPreview(
    previewDetails.copy(
        name = "Solo Mint",
        network = FederationNetwork.Bitcoin,
        guardianCount = 1,
        modules = listOf("mint", "ln", "wallet"),
        meta = emptyMap(),
        inviteGuardians = listOf(FederationGuardian(0, "wss://solo.example.com/")),
    ),
    MintConnectionState.Online,
    active = false,
)

@PreviewTest
@Preview(name = "federation-large-text", widthDp = 320, heightDp = 2200, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun federationDetailLargeText() = FederationPreview(previewDetails.copy(guardianCount = 7), MintConnectionState.Online)
