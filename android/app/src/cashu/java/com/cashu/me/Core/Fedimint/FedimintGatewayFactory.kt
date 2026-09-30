package com.cashu.me.Core.Fedimint

import android.content.Context
import com.cashu.me.Core.CDK.WalletGateway

/** `cashu` flavor: no Fedimint SDK, so the Cashu gateway is used unchanged. */
object FedimintGatewayFactory {
    @Suppress("UNUSED_PARAMETER")
    fun wrap(base: WalletGateway, context: Context): WalletGateway = base
}
