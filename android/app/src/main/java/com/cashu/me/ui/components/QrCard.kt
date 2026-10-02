package com.cashu.me.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cashu.me.Core.WalletHaptic
import com.cashu.me.Core.rememberWalletHaptics
import com.cashu.me.Views.Components.QRCodeView
import com.cashu.me.ui.theme.CashuTheme

/**
 * White-cushioned wrapper around the legacy QRCodeView (which is off-limits per memory).
 * A tap copies the content (DESIGN.md → QR Tap-to-Copy Rule; iOS `QRCodeView`
 * parity); long-press exposes a Copy / Share dropdown. The 20dp corner comes from
 * the M3 'large' shape token; 16dp padding cushions the QR off the white surface.
 *
 * @param copyLabel what a tap copies, read by TalkBack ("Copy Bitcoin address").
 *   Never a bare "Copy": that names the screen's own Copy button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QrCard(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    showQrControls: Boolean = false,
    staticOnly: Boolean = false,
    shareSubject: String = "Cashu",
    confirmationMessage: String = "Copied",
    copyLabel: String = "Copy QR code",
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val walletHaptics = rememberWalletHaptics()
    val confirmationToastController = LocalConfirmationToastController.current
    var menuOpen by remember { mutableStateOf(false) }
    val copy = {
        clipboard.setText(AnnotatedString(content))
        confirmationToastController?.show(confirmationMessage)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(Color.White)
                .semantics {
                    contentDescription = copyLabel
                    role = Role.Button
                }
                .combinedClickable(
                    onClick = {
                        walletHaptics.perform(WalletHaptic.Success)
                        copy()
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onClickLabel = "Copy",
                    onLongClickLabel = "Show options",
                )
                .padding(CashuTheme.spacing.comfortable)
                .size(size),
        ) {
            QRCodeView(
                content = content,
                modifier = Modifier.fillMaxWidth(),
                showControls = showQrControls,
                staticOnly = staticOnly,
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = MaterialTheme.shapes.large,
        ) {
            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    copy()
                },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Outlined.IosShare, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    context.shareText(content, shareSubject)
                },
            )
        }
    }
}

internal fun Context.shareText(text: String, subject: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(chooser)
}
