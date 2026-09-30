package com.cashu.me.Core.Fedimint

import android.content.Context
import com.cashu.me.Core.CDK.CompositeWalletGateway
import com.cashu.me.Core.CDK.WalletGateway
import org.fedimint.sdk.Notes

/** `fedimint` flavor: layer the Fedimint backend on top of the Cashu gateway. */
object FedimintGatewayFactory {
    fun wrap(base: WalletGateway, context: Context): WalletGateway {
        FedimintSupport.notesParser = { raw ->
            runCatching {
                val notes = Notes.parse(raw)
                try {
                    (notes.value() / 1000UL).toLong()
                } finally {
                    notes.close()
                }
            }.getOrNull()
        }
        return CompositeWalletGateway(base, FedimintWalletGateway(context))
    }
}
