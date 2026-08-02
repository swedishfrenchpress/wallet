package com.cashu.me.ui.navigation

// The shell is the one place where system back has more than one candidate
// outcome (which activity-window overlay closes first). Flow-sheet screens
// need no policy: dismissal is the sheet's own job — back = swipe = abandon
// to the wallet — and each screen just swallows back while money is moving.
enum class ShellBackAction {
    CloseReceiveDetail,
    CloseScanner,
}

fun shellBackAction(
    receiveDetailVisible: Boolean,
    scannerVisible: Boolean,
): ShellBackAction? =
    when {
        receiveDetailVisible -> ShellBackAction.CloseReceiveDetail
        scannerVisible -> ShellBackAction.CloseScanner
        else -> null
    }
