package com.cashu.me.ui.mints

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cashu.me.Core.AmountFormatter
import com.cashu.me.Core.PriceService
import com.cashu.me.Core.SettingsManager
import com.cashu.me.Core.Wallet.userFacingWalletMessage
import com.cashu.me.Core.WalletManager
import com.cashu.me.Core.shortenMintUrl
import com.cashu.me.Models.FederationDetails
import com.cashu.me.Models.FederationGuardian
import com.cashu.me.Models.FederationQuorum
import com.cashu.me.Models.FederationState
import com.cashu.me.Models.MintInfo
import com.cashu.me.Models.PaymentMethodKind
import com.cashu.me.ui.components.ActionConfirmationSheet
import com.cashu.me.ui.components.DestructiveTextButton
import com.cashu.me.ui.components.InlineNotice
import com.cashu.me.ui.components.InspectorRow
import com.cashu.me.ui.components.InspectorRowStyle
import com.cashu.me.ui.components.LocalConfirmationToastController
import com.cashu.me.ui.components.MintAvatar
import com.cashu.me.ui.components.NoticeSeverity
import com.cashu.me.ui.components.PrimaryButton
import com.cashu.me.ui.components.SectionHeader
import com.cashu.me.ui.components.SkeletonValue
import com.cashu.me.ui.components.TextButtonContext
import com.cashu.me.ui.components.ToolbarIcon
import com.cashu.me.ui.components.neutralActionButtonColors
import com.cashu.me.ui.components.openInBrowser
import com.cashu.me.ui.settings.QrDetailSheet
import com.cashu.me.ui.testing.UiTestTags
import com.cashu.me.ui.theme.CapsuleShape
import com.cashu.me.ui.theme.CashuTheme
import com.cashu.me.ui.theme.withMonoDigits
import com.cashu.me.ui.theme.withSlashedZero
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException

/**
 * Detail screen for a Fedimint federation (a mint keyed `fedimint:<id>`).
 *
 * Same shape as [MintDetailScreen] (hero header, balance and connection, quiet
 * inspector sections, then the default/remove actions), but fed by the
 * Fedimint SDK instead of NUT-06: the guardians and their quorum, network,
 * modules, the invite, and the federation's own metadata. A local snapshot
 * renders at once and offline; the guardians' view replaces it when a live
 * read lands, and a failed read keeps the snapshot with Retry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FederationDetailScreen(
    walletManager: WalletManager,
    settingsManager: SettingsManager,
    priceService: PriceService,
    mintUrl: String,
    onClose: () -> Unit,
) {
    val walletState by walletManager.state.collectAsState()
    val settings by settingsManager.state.collectAsState()
    val priceState by priceService.state.collectAsState()
    val mint = walletState.mints.firstOrNull { it.url == mintUrl }
    val isActive = walletState.activeMint?.url == mintUrl
    var confirmingRemove by remember { mutableStateOf(false) }
    var settingDefault by remember(mintUrl) { mutableStateOf(false) }
    var setDefaultError by remember(mintUrl) { mutableStateOf<String?>(null) }
    var removing by remember(mintUrl) { mutableStateOf(false) }
    var removalError by remember(mintUrl) { mutableStateOf<String?>(null) }
    var showingInvite by remember(mintUrl) { mutableStateOf(false) }

    var snapshot by remember(mintUrl) { mutableStateOf<FederationDetails?>(null) }
    LaunchedEffect(mintUrl) {
        try {
            snapshot = walletManager.federationDetails(mintUrl, live = false)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The live read below reports reachability; the header falls back to the stored mint row.
        }
    }
    val liveLoader = remember(mintUrl) { MintDetailInfoLoader<FederationDetails>() }
    var refreshNonce by remember(mintUrl) { mutableStateOf(0) }
    LaunchedEffect(mintUrl, refreshNonce) {
        liveLoader.load { walletManager.federationDetails(mintUrl, live = true) }
    }
    val details = liveLoader.info ?: snapshot
    val title = details?.name ?: mint?.name ?: "Federation"

    Scaffold(
        modifier = Modifier.testTag(UiTestTags.MintDetailScreen),
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        ToolbarIcon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (mint == null) {
            EmptyMintFallback(padding = padding, onClose = onClose)
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .testTag(UiTestTags.MintDetailContent),
        ) {
            FederationDetailBody(
                mint = mint,
                isActive = isActive,
                details = details,
                connection = liveLoader.connection,
                showsRecovery = liveLoader.errorMessage != null,
                onRetry = { refreshNonce += 1 },
                balanceSecondary = mintSatBalanceFiatSecondary(
                    balanceSats = mint.balance,
                    showFiat = settings.showFiatBalance,
                    btcPrice = priceState.btcPrice,
                    currencyCode = settings.bitcoinPriceCurrency,
                ),
                onShowInvite = { showingInvite = true },
            )

            Spacer(Modifier.height(CashuTheme.spacing.comfortable))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = CashuTheme.spacing.comfortable),
                verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.snug),
            ) {
                setDefaultError?.let { InlineNotice(text = it, severity = NoticeSeverity.Error) }
                removalError?.let { InlineNotice(text = it, severity = NoticeSeverity.Error) }
                if (!isActive) {
                    PrimaryButton(
                        text = "Set as Default",
                        loading = settingDefault,
                        onClick = {
                            if (settingDefault) return@PrimaryButton
                            settingDefault = true
                            setDefaultError = null
                            walletManager.launch {
                                runCatching { walletManager.setActiveMint(mint) }
                                    .onFailure { setDefaultError = it.userFacingWalletMessage }
                                settingDefault = false
                            }
                        },
                        colors = neutralActionButtonColors(),
                    )
                }
                DestructiveTextButton(
                    context = TextButtonContext.Screen,
                    text = "Remove federation",
                    onClick = { confirmingRemove = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !removing,
                )
            }
            Spacer(Modifier.height(CashuTheme.spacing.section))
        }
    }

    val invite = details?.inviteCode?.takeIf { it.isNotBlank() }
    if (showingInvite && invite != null) {
        QrDetailSheet(title = "Invite code", content = invite, onDismiss = { showingInvite = false })
    }

    if (confirmingRemove) {
        ActionConfirmationSheet(
            title = "Remove federation?",
            message = "Remove $title from your wallet? You can rejoin it later with its invite code.",
            actionLabel = "Remove",
            destructive = true,
            onConfirm = {
                confirmingRemove = false
                val target = mint ?: return@ActionConfirmationSheet
                removing = true
                removalError = null
                walletManager.launch {
                    try {
                        walletManager.removeMint(target)
                        onClose()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Throwable) {
                        removalError = error.userFacingWalletMessage
                    } finally {
                        removing = false
                    }
                }
            },
            onDismiss = { confirmingRemove = false },
        )
    }
}

/** Everything above the actions; stateless so previews and screenshot tests can drive it. */
@Composable
internal fun FederationDetailBody(
    mint: MintInfo,
    isActive: Boolean,
    details: FederationDetails?,
    connection: MintConnectionState,
    showsRecovery: Boolean,
    onRetry: () -> Unit,
    balanceSecondary: String?,
    onShowInvite: () -> Unit,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
) {
    val checking = connection == MintConnectionState.Checking
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.snug),
    ) {
        FederationHeader(mint = mint, details = details, isActive = isActive)

        Column(modifier = Modifier.fillMaxWidth()) {
            InspectorRow(
                style = InspectorRowStyle.Standard,
                label = "Balance",
                value = "${mint.balance} sat",
                secondaryValue = balanceSecondary,
                leadingIcon = Icons.Outlined.CurrencyBitcoin,
                valueMonospaced = true,
            )
            MintConnectionStatus(
                connection = connection,
                showsRecovery = showsRecovery,
                onRetry = onRetry,
                subject = "federation",
            )
        }

        FederationNotices(details = details, nowEpochSeconds = nowEpochSeconds)

        details?.welcomeMessage?.let { welcome ->
            SectionHeader("Message from the federation")
            Text(
                text = welcome,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = CashuTheme.spacing.comfortable),
            )
        }

        GuardiansSection(details = details, checking = checking)

        // Rails the app can drive here: federations settle Lightning over BOLT11.
        val rails = when {
            details == null -> mint.effectiveMintMethods
            details.supportsLightning -> listOf(PaymentMethodKind.Bolt11)
            else -> emptyList()
        }
        if (rails.isNotEmpty()) {
            SectionHeader("Payment methods")
            Column(modifier = Modifier.fillMaxWidth()) {
                InspectorRow(
                    style = InspectorRowStyle.Standard,
                    label = "Receive",
                    value = rails.joinToString(" · ") { it.displayName },
                    leadingIcon = Icons.Outlined.ArrowDownward,
                )
                InspectorRow(
                    style = InspectorRowStyle.Standard,
                    label = "Send",
                    value = rails.joinToString(" · ") { it.displayName },
                    leadingIcon = Icons.Outlined.ArrowUpward,
                )
            }
        }

        FederationDetailsSection(details = details, checking = checking, onShowInvite = onShowInvite)

        if (details != null) FederationTechnicalDetails(details)

        // Provenance, as on the mint screen: names, messages and limits are the
        // guardians' own claims, not something the wallet verified.
        Text(
            text = "Information reported by the federation's guardians.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = CashuTheme.spacing.comfortable),
        )
    }
}

@Composable
private fun FederationHeader(mint: MintInfo, details: FederationDetails?, isActive: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CashuTheme.spacing.comfortable, vertical = CashuTheme.spacing.comfortable),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.default),
    ) {
        Box {
            MintAvatar(mint = mint.copy(iconUrl = details?.iconUrl ?: mint.iconUrl), size = 72.dp)
            if (isActive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(CashuTheme.spacing.comfortable)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Active",
                        tint = CashuTheme.colors.onReceivedContainer,
                        modifier = Modifier.size(CashuTheme.spacing.default),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = details?.name ?: mint.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = federationSubtitle(details?.guardianCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        CopyValueChip(
            text = shortenMintUrl(mint.url),
            value = details?.federationId ?: mint.url.removePrefix("fedimint:"),
            label = "federation ID",
        )
        val network = details?.network?.takeUnless { it.isMainnet }
        if (isActive || network != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.snug)) {
                if (isActive) {
                    HeaderPill(
                        text = "Default mint",
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (network != null) {
                    // Test-network federations hold worthless coins; say so up front.
                    HeaderPill(
                        text = network.displayName,
                        container = CashuTheme.colors.pendingContainer,
                        content = CashuTheme.colors.onPendingContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderPill(text: String, container: Color, content: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .clip(CapsuleShape)
            .background(container)
            .padding(horizontal = CashuTheme.spacing.default, vertical = CashuTheme.spacing.tight),
    )
}

/** Same chip as the mint screen's URL chip: a fixed 8…6 cut that copies the full value. */
@Composable
private fun CopyValueChip(text: String, value: String, label: String) {
    val clipboard = LocalClipboardManager.current
    val confirmationToastController = LocalConfirmationToastController.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.tight),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClickLabel = "Copy $label") {
                clipboard.setText(AnnotatedString(value))
                confirmationToastController?.show("Copied $label")
            }
            .padding(horizontal = CashuTheme.spacing.snug, vertical = CashuTheme.spacing.tight),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = CashuTheme.fonts.mono).withSlashedZero(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = "Copy $label",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Lifecycle, shutdown and guardian announcements, most serious first. */
@Composable
private fun FederationNotices(details: FederationDetails?, nowEpochSeconds: Long) {
    if (details == null) return
    val notices = buildList {
        when (val state = details.state) {
            FederationState.Recovering -> add(
                NoticeSeverity.Caution to "Restoring this federation's balance from your seed. Payments resume when it finishes.",
            )
            is FederationState.Quarantined -> add(NoticeSeverity.Error to "This federation couldn't be opened. ${state.reason}")
            FederationState.Closed -> add(NoticeSeverity.Error to "This federation is closed in this wallet.")
            FederationState.Running -> Unit
        }
        details.expiresAtEpochSeconds?.let { expiry ->
            val date = formatFederationDate(expiry)
            add(
                if (expiry > nowEpochSeconds) {
                    NoticeSeverity.Caution to "The guardians plan to shut this federation down on $date. Move your funds out before then."
                } else {
                    NoticeSeverity.Error to "The guardians shut this federation down on $date."
                },
            )
        }
        details.activePopup(nowEpochSeconds)?.let { add(NoticeSeverity.Info to it) }
    }
    if (notices.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CashuTheme.spacing.comfortable),
        verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.snug),
    ) {
        notices.forEach { (severity, text) -> InlineNotice(text = text, severity = severity, showsContainer = true) }
    }
}

/**
 * The quorum card (how many guardians, how many must agree, how many may be
 * offline) followed by the guardians the invite names, each with its endpoint.
 */
@Composable
private fun GuardiansSection(details: FederationDetails?, checking: Boolean) {
    val clipboard = LocalClipboardManager.current
    val confirmationToastController = LocalConfirmationToastController.current
    val quorum = details?.quorum
    val listed = details?.inviteGuardians.orEmpty()
    SectionHeader("Guardians")
    QuorumCard(quorum = quorum, checking = checking)
    if (listed.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = CashuTheme.spacing.micro)) {
            listed.forEach { guardian ->
                GuardianRow(guardian) {
                    clipboard.setText(AnnotatedString(guardian.url))
                    confirmationToastController?.show("Copied guardian address")
                }
            }
        }
    }
    val listedNote = when {
        quorum == null || listed.isEmpty() -> null
        listed.size >= quorum.guardians -> null
        else -> "The invite code lists ${listed.size} of ${quorum.guardians} guardian addresses."
    }
    listedNote?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = CashuTheme.spacing.comfortable),
        )
    }
}

/** List-row shape of the Mints list: glyph, name, full endpoint host; tap copies the address. */
@Composable
private fun GuardianRow(guardian: FederationGuardian, onCopy: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.default),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = "Copy guardian address", onClick = onCopy)
            .padding(horizontal = CashuTheme.spacing.comfortable, vertical = CashuTheme.spacing.snug),
    ) {
        Box(
            modifier = Modifier
                .size(GuardianGlyphSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = guardianLabel(guardian),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = guardian.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun QuorumCard(quorum: FederationQuorum?, checking: Boolean) {
    val summary = quorum?.let(::quorumSummary)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CashuTheme.spacing.comfortable)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(CashuTheme.spacing.comfortable)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .semantics(mergeDescendants = true) {
                summary?.let { contentDescription = quorumAccessibilityLabel(it, quorum) }
            },
        verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.default),
    ) {
        val configuration = LocalConfiguration.current
        val stacked = configuration.fontScale > 1.3f || configuration.screenWidthDp < 360
        val count: @Composable () -> Unit = {
            Column {
                SkeletonValue(loading = quorum == null && checking) {
                    Text(
                        text = quorum?.guardians?.toString() ?: "–",
                        style = MaterialTheme.typography.headlineMedium.withMonoDigits(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = if (quorum?.guardians == 1) "Guardian" else "Guardians",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // A lone guardian is its own threshold; "1 of 1 must agree" says nothing.
        val threshold: (@Composable () -> Unit)? = quorum?.takeIf { it.guardians > 1 }?.let { known ->
            {
                Column(horizontalAlignment = if (stacked) Alignment.Start else Alignment.End) {
                    Text(
                        text = "${known.threshold} of ${known.guardians}",
                        style = MaterialTheme.typography.titleMedium.withMonoDigits(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "must agree",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.snug)) {
                count()
                threshold?.invoke()
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.weight(1f)) { count() }
                threshold?.invoke()
            }
        }
        QuorumBar(quorum = quorum)
        Text(
            text = summary ?: if (checking) "Asking the guardians…" else "Guardian details appear when the federation is reachable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One segment per guardian: filled for the signing threshold, outlined for the
 * spares the federation can lose. Large federations collapse to two spans.
 */
@Composable
private fun QuorumBar(quorum: FederationQuorum?) {
    val filled = MaterialTheme.colorScheme.onSurface
    val spare = MaterialTheme.colorScheme.onSurfaceVariant
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier.fillMaxWidth().height(QuorumBarHeight),
        horizontalArrangement = Arrangement.spacedBy(QuorumSegmentGap),
    ) {
        when {
            quorum == null -> QuorumSegment(1f, color = empty)
            quorum.guardians <= MaxQuorumSegments -> repeat(quorum.guardians) { index ->
                QuorumSegment(1f, color = filled, spareOutline = spare.takeIf { index >= quorum.threshold })
            }
            else -> {
                QuorumSegment(quorum.threshold.toFloat(), color = filled)
                if (quorum.tolerated > 0) QuorumSegment(quorum.tolerated.toFloat(), color = filled, spareOutline = spare)
            }
        }
    }
}

@Composable
private fun RowScope.QuorumSegment(weight: Float, color: Color, spareOutline: Color? = null) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(CapsuleShape)
            .then(if (spareOutline != null) Modifier.border(1.dp, spareOutline, CapsuleShape) else Modifier.background(color)),
    )
}

@Composable
private fun FederationDetailsSection(details: FederationDetails?, checking: Boolean, onShowInvite: () -> Unit) {
    val context = LocalContext.current
    val formatter = remember { AmountFormatter() }
    val tosUrl = safeExternalHttpUrl(details?.tosUrl)
    SectionHeader("Details")
    Column(modifier = Modifier.fillMaxWidth()) {
        details?.let {
            InspectorRow(
                style = InspectorRowStyle.Standard,
                label = "Network",
                value = it.network.displayName,
                leadingIcon = Icons.Outlined.Hub,
            )
        }
        val modules = details?.moduleLabels.orEmpty()
        if (modules.isNotEmpty() || checking) ModulesRow(modules)
        details?.inviteCode?.takeIf { it.isNotBlank() }?.let { invite ->
            InspectorRow(
                style = InspectorRowStyle.Standard,
                label = "Invite code",
                value = middleCut(invite),
                leadingIcon = Icons.Outlined.QrCode,
                valueMonospaced = true,
                onClick = onShowInvite,
            )
        }
        details?.maxBalanceSats?.let {
            InspectorRow(
                style = InspectorRowStyle.Standard,
                label = "Balance limit",
                value = formatter.formatSats(it),
                leadingIcon = Icons.Outlined.Savings,
                valueMonospaced = true,
            )
        }
        details?.maxInvoiceSats?.let {
            InspectorRow(
                style = InspectorRowStyle.Standard,
                label = "Invoice limit",
                value = formatter.formatSats(it),
                leadingIcon = Icons.Outlined.Bolt,
                valueMonospaced = true,
            )
        }
        if (tosUrl != null) {
            InspectorRow(
                style = InspectorRowStyle.Standard,
                label = "Terms of Service",
                value = externalUrlHost(tosUrl) ?: tosUrl,
                leadingIcon = Icons.Outlined.Description,
                onClick = { context.openInBrowser(tosUrl) },
                trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
            )
        }
    }
}

/**
 * Inspector-row metrics with the value as wrapping tags: a federation can run
 * any number of modules, and a joined list would middle-truncate.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModulesRow(labels: List<String>) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.default),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CashuTheme.spacing.comfortable, vertical = CashuTheme.spacing.default)
            .semantics(mergeDescendants = true) {
                if (labels.isNotEmpty()) contentDescription = "Modules: ${labels.joinToString(", ")}"
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.default),
            modifier = Modifier.heightIn(min = ModuleTagMinHeight),
        ) {
            Icon(
                imageVector = Icons.Outlined.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text("Modules", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            SkeletonValue(loading = labels.isEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.tight, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.tight),
                ) {
                    labels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .heightIn(min = ModuleTagMinHeight)
                                .clip(CapsuleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = CashuTheme.spacing.snug, vertical = CashuTheme.spacing.micro),
                        )
                    }
                }
            }
        }
    }
}

/** The raw SDK view behind the rows above, collapsed like the mint screen's NUT list. */
@Composable
private fun FederationTechnicalDetails(details: FederationDetails) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "federationTechChevron")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = CashuTheme.spacing.comfortable, vertical = CashuTheme.spacing.default),
        ) {
            Text(
                text = "Technical details",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronRotation },
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(bottom = CashuTheme.spacing.snug),
                verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.snug),
            ) {
                TechnicalEntry("federation_id", details.federationId)
                if (details.modules.isNotEmpty()) TechnicalEntry("modules", details.modules.joinToString(", "))
                TechnicalEntry(
                    "capabilities",
                    buildList {
                        if (details.supportsEcash) add("ecash")
                        if (details.supportsLightning) add("lightning")
                        if (details.supportsOnchain) add("onchain")
                    }.joinToString(", ").ifEmpty { "none" },
                )
                details.metaRevision?.let { TechnicalEntry("meta_revision", it.toString()) }
                details.inviteGuardians.forEach { TechnicalEntry("peer_${it.peerId}", it.url) }
                details.meta.toSortedMap().forEach { (key, value) -> TechnicalEntry(key, value) }
            }
        }
    }
}

@Composable
private fun TechnicalEntry(key: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CashuTheme.spacing.comfortable),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = CashuTheme.fonts.mono).withSlashedZero(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun federationSubtitle(guardianCount: Int?): String = when (guardianCount) {
    null -> "Fedimint federation"
    1 -> "Fedimint federation · 1 guardian"
    else -> "Fedimint federation · $guardianCount guardians"
}

internal fun quorumSummary(quorum: FederationQuorum): String = when {
    quorum.guardians == 1 -> "Run by a single guardian, with no backup if it goes offline."
    quorum.tolerated == 0 -> "All ${quorum.guardians} guardians must be online to sign."
    quorum.tolerated == 1 -> "Keeps working with 1 guardian offline."
    else -> "Keeps working with up to ${quorum.tolerated} guardians offline."
}

private fun quorumAccessibilityLabel(summary: String, quorum: FederationQuorum): String =
    "${quorum.guardians} guardians, ${quorum.threshold} must agree. $summary"

private fun guardianLabel(guardian: FederationGuardian): String = "Guardian ${guardian.peerId + 1}"

/** The opaque-string convention: `prefix(8)…suffix(6)`, never width-filled. */
private fun middleCut(value: String): String =
    if (value.length > 16) "${value.take(8)}…${value.takeLast(6)}" else value

private fun formatFederationDate(epochSeconds: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))

private val QuorumBarHeight = 8.dp
private val GuardianGlyphSize = 40.dp
private val ModuleTagMinHeight = 28.dp
private val QuorumSegmentGap = 4.dp
private const val MaxQuorumSegments = 16
