package com.cashu.me.ui.mints

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashu.me.Core.AmountDisplayText
import com.cashu.me.Core.AmountFormatter
import com.cashu.me.Core.Fedimint.FedimintSupport
import com.cashu.me.Core.PriceService
import com.cashu.me.Core.SettingsManager
import com.cashu.me.Core.Wallet.userFacingWalletMessage
import com.cashu.me.Core.WalletManager
import com.cashu.me.Core.displayText
import com.cashu.me.Models.FederationDetails
import com.cashu.me.Models.FederationGuardian
import com.cashu.me.Models.FederationQuorum
import com.cashu.me.Models.FederationState
import com.cashu.me.Models.GuardianHealth
import com.cashu.me.Models.MintInfo
import com.cashu.me.ui.components.ActionConfirmationSheet
import com.cashu.me.ui.components.AmountHero
import com.cashu.me.ui.components.AmountText
import com.cashu.me.ui.components.DestructiveTextButton
import com.cashu.me.ui.components.GhostButton
import com.cashu.me.ui.components.InlineNotice
import com.cashu.me.ui.components.LocalConfirmationToastController
import com.cashu.me.ui.components.MintAvatar
import com.cashu.me.ui.components.NoticeSeverity
import com.cashu.me.ui.components.PrimaryButton
import com.cashu.me.ui.components.SectionHeader
import com.cashu.me.ui.components.TextButtonContext
import com.cashu.me.ui.components.ToolbarIcon
import com.cashu.me.ui.components.neutralActionButtonColors
import com.cashu.me.ui.components.noticeColors
import com.cashu.me.ui.components.openInBrowser
import com.cashu.me.ui.settings.QrDetailSheet
import com.cashu.me.ui.testing.UiTestTags
import com.cashu.me.ui.theme.AmountScale
import com.cashu.me.ui.theme.CapsuleShape
import com.cashu.me.ui.theme.CashuTheme
import com.cashu.me.ui.theme.LeadingLabel
import com.cashu.me.ui.theme.atSize
import com.cashu.me.ui.theme.withMonoDigits
import com.cashu.me.ui.theme.withSlashedZero
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity

/**
 * Profile of a Fedimint federation (a mint keyed `fedimint:<id>`).
 *
 * A hero (who it is, what you hold there), then inset cards in reading
 * order: a lifecycle card when the federation is ending or can't run, the
 * guardians (the one place connection status lives), what the federation
 * says about itself, what you can do with it, and the details behind it.
 * A local snapshot renders at once and offline; the guardians' view replaces
 * it when a live read lands.
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
    var titleScrolledAway by remember(mintUrl) { mutableStateOf(false) }
    val formatter = remember { AmountFormatter() }

    var snapshot by remember(mintUrl) { mutableStateOf<FederationDetails?>(null) }
    LaunchedEffect(mintUrl) {
        try {
            snapshot = walletManager.federationDetails(mintUrl, live = false)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The live read below reports reachability; the hero falls back to the stored mint row.
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
                // The hero carries the name; the bar picks it up once the hero scrolls away.
                title = {
                    AnimatedVisibility(visible = titleScrolledAway, enter = fadeIn(), exit = fadeOut()) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
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
                onRetry = { refreshNonce += 1 },
                balance = formatter.displayText(
                    amountSats = mint.balance,
                    preferredPrimary = settings.amountDisplayPrimary,
                    showFiat = settings.showFiatBalance,
                    btcPrice = priceState.btcPrice,
                    currencyCode = settings.bitcoinPriceCurrency,
                    useBitcoinSymbol = settings.useBitcoinSymbol,
                ),
                useBitcoinSymbol = settings.useBitcoinSymbol,
                onShowInvite = { showingInvite = true },
                onTitleScrolledAway = { titleScrolledAway = it },
            )

            Spacer(Modifier.height(CashuTheme.spacing.section))
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
    onRetry: () -> Unit,
    balance: AmountDisplayText,
    useBitcoinSymbol: Boolean,
    onShowInvite: () -> Unit,
    onTitleScrolledAway: (Boolean) -> Unit = {},
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    zone: ZoneId = ZoneId.systemDefault(),
    technicalExpanded: Boolean = false,
) {
    val formatter = remember { AmountFormatter() }
    val lifecycle = details?.let { federationLifecycle(it, nowEpochSeconds, zone) }
    val paymentRows = details?.let { federationPaymentRows(it, formatter, useBitcoinSymbol) }.orEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        FederationHero(
            mint = mint,
            details = details,
            isActive = isActive,
            balance = balance,
            onTitleScrolledAway = onTitleScrolledAway,
        )

        // External meta (where Fedi federations publish an end date) can land
        // with the live read, after the first frame; let the card slide in.
        AnimatedVisibility(
            visible = lifecycle != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            lifecycle?.let { FederationLifecycleCard(it, Modifier.padding(top = CashuTheme.spacing.default)) }
        }

        GuardiansCard(details = details, status = guardianStatusCopy(details, connection), onRetry = onRetry)

        details?.aboutMessage?.let { about ->
            SectionHeader("About")
            FederationCard {
                ClampedText(
                    text = about,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = CashuTheme.spacing.comfortable, bottom = CashuTheme.spacing.default),
                )
            }
        }

        if (paymentRows.isNotEmpty()) {
            SectionHeader("Payments")
            FederationCard {
                paymentRows.forEachIndexed { index, row ->
                    if (index > 0) CardDivider()
                    CardRow(label = row.label, value = row.value, digits = row.digits)
                }
            }
        }

        FederationDetailsCard(mint = mint, details = details, onShowInvite = onShowInvite, technicalExpanded = technicalExpanded)

        // Provenance, as on the mint screen: names, messages and limits are the
        // guardians' own claims, not something the wallet verified.
        Text(
            text = "Information reported by the federation's guardians.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = CashuTheme.spacing.comfortable, end = CashuTheme.spacing.comfortable, top = CashuTheme.spacing.comfortable),
        )
    }
}

// ---- hero ----------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FederationHero(
    mint: MintInfo,
    details: FederationDetails?,
    isActive: Boolean,
    balance: AmountDisplayText,
    onTitleScrolledAway: (Boolean) -> Unit,
) {
    val network = details?.network?.takeUnless { it.isMainnet }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CashuTheme.spacing.comfortable)
            .padding(top = CashuTheme.spacing.snug, bottom = CashuTheme.spacing.snug),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.default),
    ) {
        MintAvatar(mint = mint.copy(iconUrl = details?.iconUrl ?: mint.iconUrl), size = 72.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = details?.name ?: mint.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .semantics { heading() }
                    // The scroll container clips this, so a shrinking visible height
                    // means the name is passing under the top bar.
                    .onGloballyPositioned { coordinates ->
                        onTitleScrolledAway(coordinates.boundsInRoot().height < coordinates.size.height / 2f)
                    },
            )
            Text(
                text = "Fedimint federation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isActive || network != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.snug, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.snug),
            ) {
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
        HeroBalance(balance = balance, modifier = Modifier.padding(top = CashuTheme.spacing.snug))
    }
}

/** The amount held here, set like the receipt hero: big primary, quiet secondary. */
@Composable
private fun HeroBalance(balance: AmountDisplayText, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AmountHero(parts = balance.primaryParts, scale = AmountScale.Confirm, accessibilityPrefix = "Balance")
        balance.secondary?.let { secondary ->
            AmountText(
                text = secondary,
                style = MaterialTheme.typography.bodyLarge
                    .atSize(18.sp, leading = LeadingLabel)
                    .copy(fontWeight = FontWeight.Medium)
                    .withMonoDigits(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                animated = false,
            )
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

// ---- lifecycle -------------------------------------------------------------------

@Composable
private fun FederationLifecycleCard(lifecycle: FederationLifecycle, modifier: Modifier = Modifier) {
    val (icon, _, container) = noticeColors(lifecycle.severity)
    val ink = when (lifecycle.severity) {
        NoticeSeverity.Error -> MaterialTheme.colorScheme.onErrorContainer
        NoticeSeverity.Caution -> CashuTheme.colors.onPendingContainer
        NoticeSeverity.Success -> CashuTheme.colors.onReceivedContainer
        NoticeSeverity.Info -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = modifier.fillMaxWidth().padding(horizontal = CashuTheme.spacing.comfortable),
    ) {
        Column(
            modifier = Modifier.padding(
                top = CashuTheme.spacing.comfortable,
                bottom = if (lifecycle.message == null) CashuTheme.spacing.comfortable else CashuTheme.spacing.default,
            ),
            verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.tight),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.snug),
                modifier = Modifier
                    .padding(horizontal = CashuTheme.spacing.comfortable)
                    .semantics(mergeDescendants = true) {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
                Text(text = lifecycle.headline, style = MaterialTheme.typography.titleSmall, color = ink)
            }
            // The headline carries the severity; the guardians' prose reads in neutral ink.
            lifecycle.message?.let {
                ClampedText(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// ---- guardians -------------------------------------------------------------------

/**
 * How many guardians are online and signing, then each guardian. The status
 * row is the screen's only connection indicator. Tapping a guardian copies
 * its address.
 */
@Composable
private fun GuardiansCard(details: FederationDetails?, status: GuardianStatusCopy, onRetry: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val confirmationToastController = LocalConfirmationToastController.current
    val guardians = details?.guardians.orEmpty()
    val healthKnown = details?.healthKnown == true
    SectionHeader("Guardians")
    FederationCard {
        GuardianStatusRow(details = details, guardians = guardians, status = status, onRetry = onRetry)
        guardians.forEach { guardian ->
            CardDivider(start = GuardianTextInset)
            GuardianRow(guardian = guardian.takeIf { healthKnown } ?: guardian.copy(health = GuardianHealth.Unknown)) {
                clipboard.setText(AnnotatedString(guardian.url))
                confirmationToastController?.show("Copied guardian address")
            }
        }
    }
}

@Composable
private fun GuardianStatusRow(
    details: FederationDetails?,
    guardians: List<FederationGuardian>,
    status: GuardianStatusCopy,
    onRetry: () -> Unit,
) {
    val stacked = LocalConfiguration.current.fontScale > 1.3f
    val healthKnown = details?.healthKnown == true
    val count = details?.quorum?.guardians
    val retry: @Composable () -> Unit = {
        TextButton(
            onClick = onRetry,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) { Text("Retry") }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(CashuTheme.spacing.comfortable),
        verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.snug),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.default),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.comfortable),
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics {
                        contentDescription = listOfNotNull(status.title, status.subtitle).joinToString(". ")
                        liveRegion = LiveRegionMode.Polite
                    },
            ) {
                Box(modifier = Modifier.size(GuardianLeadingSize), contentAlignment = Alignment.Center) {
                    if (status.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(GuardianLeadingSize),
                            strokeWidth = GuardianRingStroke,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        )
                    } else {
                        GuardianRing(
                            healths = guardians.map { it.health }.takeIf { healthKnown },
                            count = count,
                            label = if (healthKnown) details!!.onlineCount.toString() else count?.toString() ?: "–",
                            modifier = Modifier.size(GuardianLeadingSize),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = status.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    status.subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (status.warning) CashuTheme.colors.onPendingContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (status.showsRetry && !stacked) retry()
        }
        if (status.showsRetry && stacked) {
            Box(modifier = Modifier.padding(start = GuardianLeadingSize)) { retry() }
        }
    }
}

/**
 * One arc per guardian around [label]: green while signing, orange when
 * behind, faint when offline. Before health is known the arcs are neutral
 * (or a bare track when even the count is). The arcs draw in clockwise when
 * health arrives. Very large federations collapse to a single ring.
 */
@Composable
private fun GuardianRing(healths: List<GuardianHealth>?, count: Int?, label: String, modifier: Modifier = Modifier) {
    val still = LocalInspectionMode.current
    val reveal = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(healths, count) {
        if ((healths != null || count != null) && !still) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(GuardianRevealMillis, easing = FastOutSlowInEasing))
        }
    }
    val active = CashuTheme.colors.received
    val behind = CashuTheme.colors.pending
    val neutral = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = GuardianRingStroke.toPx()
            val radius = (size.minDimension - strokePx) / 2
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            val colors: List<Color> = when {
                healths != null && healths.isNotEmpty() -> healths.map {
                    when (it) {
                        GuardianHealth.Active -> active
                        GuardianHealth.Behind -> behind
                        GuardianHealth.Offline, GuardianHealth.Unknown -> faint
                    }
                }
                count != null && count > 0 -> List(count) { neutral }
                else -> emptyList()
            }
            if (colors.isEmpty()) {
                drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokePx))
                return@Canvas
            }
            val segments = if (colors.size > MaxRingSegments) listOf(colors.first()) else colors
            val slot = 360f / segments.size
            // Round caps overhang each end by half the stroke; leave a visible gap past them.
            val capDegrees = Math.toDegrees((strokePx / 2 / radius).toDouble()).toFloat()
            val gapDegrees = if (segments.size == 1) 0f else Math.toDegrees((GuardianSegmentGap.toPx() / radius).toDouble()).toFloat() + capDegrees * 2
            val drawn = 360f * reveal.value
            segments.forEachIndexed { index, color ->
                val sweep = (slot - gapDegrees).coerceAtLeast(0.5f)
                val visible = (drawn - index * slot).coerceIn(0f, sweep)
                if (visible > 0f) {
                    drawArc(
                        color = color,
                        startAngle = -90f + index * slot + gapDegrees / 2,
                        sweepAngle = visible,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokePx, cap = if (segments.size == 1) StrokeCap.Butt else StrokeCap.Round),
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.withMonoDigits(),
            color = if (healths != null || count != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Contact-row shape: monogram avatar with a presence dot, name, endpoint host. */
@Composable
private fun GuardianRow(guardian: FederationGuardian, onCopy: () -> Unit) {
    val state = guardianStateLabel(guardian.health)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.comfortable),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Copy address", onClick = onCopy)
            .padding(horizontal = CashuTheme.spacing.comfortable, vertical = CashuTheme.spacing.default)
            .semantics(mergeDescendants = true) { state?.let { stateDescription = it } },
    ) {
        Box(modifier = Modifier.size(GuardianLeadingSize), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(GuardianAvatarSize)
                    .clip(CircleShape)
                    // Ink tint, not a container tone: reads on the card in both themes.
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = guardian.monogram,
                    style = MaterialTheme.typography.titleSmall.withMonoDigits(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            presenceColor(guardian.health)?.let { dot ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                        .size(PresenceDotSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(dot),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = guardian.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = guardian.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        // Quiet when healthy; only a guardian that needs attention says so.
        if (guardian.health == GuardianHealth.Behind || guardian.health == GuardianHealth.Offline) {
            Text(
                text = state.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun presenceColor(health: GuardianHealth): Color? = when (health) {
    GuardianHealth.Active -> CashuTheme.colors.received
    GuardianHealth.Behind -> CashuTheme.colors.pending
    GuardianHealth.Offline -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    GuardianHealth.Unknown -> null
}

private fun guardianStateLabel(health: GuardianHealth): String? = when (health) {
    GuardianHealth.Active -> "Online"
    GuardianHealth.Behind -> "Behind"
    GuardianHealth.Offline -> "Offline"
    GuardianHealth.Unknown -> null
}

// ---- details -----------------------------------------------------------------------

@Composable
private fun FederationDetailsCard(
    mint: MintInfo,
    details: FederationDetails?,
    onShowInvite: () -> Unit,
    technicalExpanded: Boolean,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val confirmationToastController = LocalConfirmationToastController.current
    val federationId = details?.federationId ?: FedimintSupport.federationId(mint.url)
    val invite = details?.inviteCode?.takeIf { it.isNotBlank() }
    val tosUrl = safeExternalHttpUrl(details?.tosUrl)
    val technical = details?.let { federationTechnicalEntries(it) }.orEmpty()
    val rows = buildList<@Composable () -> Unit> {
        details?.let { add { CardRow(label = "Network", value = it.network.displayName) } }
        add {
            CardRow(
                label = "Federation ID",
                value = middleCut(federationId),
                code = true,
                trailingIcon = Icons.Outlined.ContentCopy,
                onClickLabel = "Copy federation ID",
                onClick = {
                    clipboard.setText(AnnotatedString(federationId))
                    confirmationToastController?.show("Copied federation ID")
                },
            )
        }
        invite?.let {
            add {
                CardRow(
                    label = "Invite code",
                    value = middleCut(it),
                    code = true,
                    trailingIcon = Icons.Outlined.QrCode,
                    onClickLabel = "Show QR code",
                    onClick = onShowInvite,
                )
            }
        }
        tosUrl?.let { url ->
            add {
                CardRow(
                    label = "Terms",
                    value = externalUrlHost(url) ?: url,
                    trailingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                    onClickLabel = "Open terms",
                    onClick = { context.openInBrowser(url) },
                )
            }
        }
        if (technical.isNotEmpty()) add { TechnicalDisclosure(entries = technical, initiallyExpanded = technicalExpanded) }
    }
    SectionHeader("Details")
    FederationCard {
        rows.forEachIndexed { index, row ->
            if (index > 0) CardDivider()
            row()
        }
    }
}

/** The SDK and guardian facts behind the cards above, collapsed like the mint screen's NUT list. */
@Composable
private fun TechnicalDisclosure(entries: List<TechnicalEntryCopy>, initiallyExpanded: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "federationTechChevron")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CardRowMinHeight)
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = CashuTheme.spacing.comfortable, vertical = CashuTheme.spacing.default)
                .semantics(mergeDescendants = true) { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        ) {
            Text(
                text = "Technical details",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = chevronRotation },
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(
                    start = CashuTheme.spacing.comfortable,
                    end = CashuTheme.spacing.comfortable,
                    bottom = CashuTheme.spacing.comfortable,
                ),
                verticalArrangement = Arrangement.spacedBy(CashuTheme.spacing.default),
            ) {
                entries.forEach { entry ->
                    Column(
                        modifier = Modifier.semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = entry.label,
                            style = if (entry.monoLabel) {
                                MaterialTheme.typography.labelMedium.copy(fontFamily = CashuTheme.fonts.mono).withSlashedZero()
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = entry.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---- card primitives ---------------------------------------------------------------

/** Inset grouped card, as on the Mints list. */
@Composable
private fun FederationCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CashuTheme.spacing.comfortable)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        content = content,
    )
}

@Composable
private fun CardDivider(start: Dp = CashuTheme.spacing.comfortable) {
    HorizontalDivider(
        thickness = Dp.Hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = start),
    )
}

/**
 * Label left, value right, an optional trailing glyph. At large text the
 * value moves under the label instead of truncating. [code] sets an opaque
 * string in mono with slashed zeros; [digits] gives tabular figures.
 */
@Composable
private fun CardRow(
    label: String,
    value: String,
    code: Boolean = false,
    digits: Boolean = false,
    trailingIcon: ImageVector? = null,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val stacked = LocalConfiguration.current.fontScale > 1.3f
    val valueStyle = MaterialTheme.typography.bodyLarge.let {
        when {
            code -> it.copy(fontFamily = CashuTheme.fonts.mono).withSlashedZero()
            digits -> it.withMonoDigits()
            else -> it
        }
    }
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = CardRowMinHeight)
        .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
        .padding(horizontal = CashuTheme.spacing.comfortable, vertical = CashuTheme.spacing.default)
        .semantics(mergeDescendants = true) {}
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CashuTheme.spacing.default),
    ) {
        if (stacked) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = valueStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                text = value,
                style = valueStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
        }
        trailingIcon?.let {
            Icon(imageVector = it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Long guardian prose, clamped with Read more, as the mint screen's About.
 * Whether it overflows is measured up front, so Read more is there on the
 * first frame rather than after a layout pass.
 */
@Composable
private fun ClampedText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    collapsedLines: Int = AboutCollapsedLines,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val textWidth = with(density) { (maxWidth - CashuTheme.spacing.comfortable * 2).roundToPx() }.coerceAtLeast(0)
        val overflows = remember(text, style, textWidth, collapsedLines) {
            measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = textWidth)).lineCount > collapsedLines
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = CashuTheme.spacing.comfortable),
            )
            if (overflows) {
                GhostButton(
                    context = TextButtonContext.Compact,
                    text = if (expanded) "Show less" else "Read more",
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(horizontal = CashuTheme.spacing.default),
                )
            }
        }
    }
}

// ---- copy (pure, unit-tested) -------------------------------------------------------

internal data class FederationLifecycle(val severity: NoticeSeverity, val headline: String, val message: String?)

/**
 * The one thing about the federation's life worth interrupting for, or null.
 * Can't-run states first, then the end date (expiry, else the countdown
 * announcement's end), then an unfinished recovery.
 */
internal fun federationLifecycle(
    details: FederationDetails,
    nowEpochSeconds: Long,
    zone: ZoneId,
    locale: Locale = Locale.getDefault(),
): FederationLifecycle? {
    when (val state = details.state) {
        is FederationState.Quarantined ->
            return FederationLifecycle(NoticeSeverity.Error, "Couldn't open this federation", state.reason.takeIf { it.isNotBlank() })
        FederationState.Closed -> return FederationLifecycle(NoticeSeverity.Error, "Closed in this wallet", null)
        else -> Unit
    }
    details.endsAtEpochSeconds?.let { end ->
        val date = formatFederationDate(end, zone, locale)
        return if (end <= nowEpochSeconds) {
            FederationLifecycle(NoticeSeverity.Error, "Ended $date", details.endedMessage)
        } else {
            FederationLifecycle(NoticeSeverity.Caution, "Ends $date", details.activePopup(nowEpochSeconds) ?: "Move your funds out before then.")
        }
    }
    if (details.state == FederationState.Recovering) {
        return FederationLifecycle(NoticeSeverity.Caution, "Restoring your balance", "Payments resume when it's done.")
    }
    return null
}

internal data class GuardianStatusCopy(
    val title: String,
    val subtitle: String? = null,
    val warning: Boolean = false,
    val showsRetry: Boolean = false,
    val busy: Boolean = false,
)

/**
 * The guardians card's status line, which is also the screen's connection
 * status: a federation the SDK won't run has nothing to retry, an unreachable
 * one offers Retry, and otherwise it says who is online.
 */
internal fun guardianStatusCopy(details: FederationDetails?, connection: MintConnectionState): GuardianStatusCopy {
    val count = details?.quorum?.guardians
    val countTitle = when (count) {
        null -> "Guardians"
        1 -> "1 guardian"
        else -> "$count guardians"
    }
    val state = details?.state
    if (state is FederationState.Quarantined || state == FederationState.Closed) return GuardianStatusCopy(countTitle)
    if (connection == MintConnectionState.Offline) return GuardianStatusCopy("Couldn't reach the guardians", showsRetry = true)
    details?.let(::guardianHealthCopy)?.let { return GuardianStatusCopy(it.title, it.subtitle, it.warning) }
    return when (connection) {
        MintConnectionState.Checking -> GuardianStatusCopy("Checking guardians…", busy = true)
        MintConnectionState.Online -> GuardianStatusCopy(countTitle, "Couldn't check who's online")
        else -> GuardianStatusCopy(countTitle)
    }
}

internal data class GuardianHealthCopy(val title: String, val subtitle: String?, val warning: Boolean)

/**
 * How many guardians are online, and anything wrong with signing. Quiet when
 * all is well; the signing threshold only appears once too few are signing.
 * Null until health is known.
 */
internal fun guardianHealthCopy(details: FederationDetails): GuardianHealthCopy? {
    if (!details.healthKnown) return null
    val total = details.guardianRoster.size
    val online = details.onlineCount
    val active = details.activeCount
    val behind = online - active
    val threshold = FederationQuorum(total).threshold
    if (total == 1) {
        return when {
            active == 1 -> GuardianHealthCopy("Guardian online", null, warning = false)
            online == 1 -> GuardianHealthCopy("Guardian online", "Not signing, payments paused", warning = true)
            else -> GuardianHealthCopy("Guardian offline", "Payments paused", warning = true)
        }
    }
    val title = if (online == total) "All $total online" else "$online of $total online"
    return when {
        active < threshold -> GuardianHealthCopy(title, "Payments paused · needs $threshold signing", warning = true)
        behind > 0 -> GuardianHealthCopy(title, "$behind behind", warning = false)
        else -> GuardianHealthCopy(title, null, warning = false)
    }
}

internal data class DetailRowCopy(val label: String, val value: String, val digits: Boolean = false)

/** What you can do here, in product words, and the guardians' limits. */
internal fun federationPaymentRows(
    details: FederationDetails,
    formatter: AmountFormatter,
    useBitcoinSymbol: Boolean,
): List<DetailRowCopy> = buildList {
    val supports = buildList {
        if (details.supportsLightning) add("Lightning")
        if (details.supportsEcash) add("Ecash")
    }
    if (supports.isNotEmpty()) add(DetailRowCopy("Supports", supports.joinToString(" · ")))
    details.maxBalanceSats?.let { add(DetailRowCopy("Max balance", formatter.formatSats(it, useBitcoinSymbol = useBitcoinSymbol), digits = true)) }
    details.maxInvoiceSats?.let { add(DetailRowCopy("Max payment", formatter.formatSats(it, useBitcoinSymbol = useBitcoinSymbol), digits = true)) }
}

internal data class TechnicalEntryCopy(val label: String, val value: String, val monoLabel: Boolean = false)

/**
 * Readable facts first (modules as the federation names them, the signing
 * threshold, consensus progress), then raw scalar metadata by key.
 */
internal fun federationTechnicalEntries(details: FederationDetails, locale: Locale = Locale.getDefault()): List<TechnicalEntryCopy> =
    buildList {
        if (details.modules.isNotEmpty()) add(TechnicalEntryCopy("Modules", details.modules.joinToString(", ")))
        details.quorum?.takeIf { it.guardians > 1 }?.let {
            add(TechnicalEntryCopy("Signing threshold", "${it.threshold} of ${it.guardians}"))
        }
        details.sessionCount?.let { add(TechnicalEntryCopy("Consensus session", NumberFormat.getIntegerInstance(locale).format(it))) }
        details.metaRevision?.let { add(TechnicalEntryCopy("Metadata revision", it.toString())) }
        details.otherMeta.forEach { (key, value) -> add(TechnicalEntryCopy(key, value, monoLabel = true)) }
    }

/** The opaque-string convention: `prefix(8)…suffix(6)`, never width-filled. */
private fun middleCut(value: String): String =
    if (value.length > 16) "${value.take(8)}…${value.takeLast(6)}" else value

private fun formatFederationDate(epochSeconds: Long, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

private val GuardianLeadingSize = 48.dp
private val GuardianAvatarSize = 40.dp
private val GuardianTextInset = 80.dp // row padding + leading column + gap
private val GuardianRingStroke = 4.dp
private val PresenceDotSize = 14.dp
private const val GuardianRevealMillis = 700
private val GuardianSegmentGap = 5.dp
private const val MaxRingSegments = 24
private val CardRowMinHeight = 52.dp
private const val AboutCollapsedLines = 3
