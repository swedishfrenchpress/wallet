package com.cashu.me.Core.CDK

import com.cashu.me.Core.Fedimint.FedimintSupport
import com.cashu.me.Models.FederationDetails
import com.cashu.me.Models.MeltQuoteInfo
import com.cashu.me.Models.MintInfo
import com.cashu.me.Models.MintQuoteInfo
import com.cashu.me.Models.PaymentMethodKind
import com.cashu.me.Models.RestoreMintResult
import com.cashu.me.Models.SendTokenResult
import com.cashu.me.Models.WalletTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Routes wallet calls between the Cashu (CDK) gateway and a Fedimint gateway.
 *
 * A federation is a mint whose url is `fedimint:<federationId>`; its quote ids
 * carry a `fm-` prefix. Everything else (mnemonic, repository lifecycle, NWC,
 * NFC, NUT-27 backup, recovery) stays on the CDK gateway untouched, so the
 * Cashu wallet behaves exactly as before.
 */
class CompositeWalletGateway(
    private val cdk: WalletGateway,
    private val fedi: CdkWalletGateway,
) : WalletGateway by cdk {

    private fun forMint(mintUrl: String?): CdkWalletGateway =
        if (FedimintSupport.isFederationKey(mintUrl)) fedi else cdk

    private fun forQuote(quoteId: String): CdkWalletGateway =
        if (FedimintSupport.isFederationQuoteId(quoteId)) fedi else cdk

    private fun forTokenString(token: String): CdkWalletGateway =
        if (FedimintSupport.extractNotes(token) != null) fedi else cdk

    override suspend fun hasWallets(): Boolean = cdk.hasWallets() || fedi.hasWallets()

    override suspend fun ensureWallet(mintUrl: String, unit: String) =
        forMint(mintUrl).ensureWallet(mintUrl, unit)

    override suspend fun maxSendableEcash(mintUrl: String, unit: String): Long? =
        forMint(mintUrl).maxSendableEcash(mintUrl, unit)

    override suspend fun joinFederation(invite: String): MintInfo = fedi.joinFederation(invite)

    override suspend fun federationDetails(mintUrl: String, live: Boolean): FederationDetails =
        fedi.federationDetails(mintUrl, live)

    override suspend fun removeWalletIfSingleUnit(mintUrl: String): Boolean =
        forMint(mintUrl).removeWalletIfSingleUnit(mintUrl)

    override suspend fun fetchMintInfo(mintUrl: String): MintInfo? =
        forMint(mintUrl).fetchMintInfo(mintUrl)

    override suspend fun restoreMint(mintUrl: String): RestoreMintResult =
        forMint(mintUrl).restoreMint(mintUrl)

    override suspend fun storedAccounts(): List<WalletAccountReference> =
        cdk.storedAccounts() + runCatching { fedi.storedAccounts() }.getOrDefault(emptyList())

    override suspend fun storedAccountBalance(account: WalletAccountReference): Long =
        forMint(account.mintUrl).storedAccountBalance(account)

    override suspend fun totalBalance(mintUrl: String): Long =
        forMint(mintUrl).totalBalance(mintUrl)

    override suspend fun unitBalance(mintUrl: String, unit: String): Long =
        forMint(mintUrl).unitBalance(mintUrl, unit)

    override suspend fun unitBalanceIfExists(mintUrl: String, unit: String): Long? =
        forMint(mintUrl).unitBalanceIfExists(mintUrl, unit)

    override suspend fun createMintQuote(
        amount: Long?,
        method: PaymentMethodKind,
        mintUrl: String,
        unit: String,
        description: String?,
    ): MintQuoteInfo = forMint(mintUrl).createMintQuote(amount, method, mintUrl, unit, description)

    override suspend fun checkMintQuote(quoteId: String): MintQuoteInfo =
        forQuote(quoteId).checkMintQuote(quoteId)

    override suspend fun storedMintQuote(quoteId: String): MintQuoteInfo? =
        forQuote(quoteId).storedMintQuote(quoteId)

    override fun subscribeToMintQuote(quoteId: String, mayRefresh: () -> Boolean): Flow<MintQuoteInfo> =
        forQuote(quoteId).subscribeToMintQuote(quoteId, mayRefresh)

    override suspend fun listUnissuedMintQuotes(): List<MintQuoteInfo> =
        cdk.listUnissuedMintQuotes() + runCatching { fedi.listUnissuedMintQuotes() }.getOrDefault(emptyList())

    override suspend fun mintTokens(quoteId: String): Long = forQuote(quoteId).mintTokens(quoteId)

    override suspend fun createMeltQuote(
        request: String,
        amountSats: Long?,
        preferredMintURL: String?,
    ): MeltQuoteInfo = forMint(preferredMintURL).createMeltQuote(request, amountSats, preferredMintURL)

    override suspend fun listMeltQuotes(): List<MeltQuoteInfo> =
        cdk.listMeltQuotes() + runCatching { fedi.listMeltQuotes() }.getOrDefault(emptyList())

    override suspend fun meltTokens(quoteId: String, mintUrl: String?): MeltConfirmation =
        forQuote(quoteId).meltTokens(quoteId, mintUrl)

    override suspend fun checkMeltQuoteStatus(quoteId: String, mintUrl: String?): MeltQuoteInfo =
        forQuote(quoteId).checkMeltQuoteStatus(quoteId, mintUrl)

    override suspend fun recoverIncompleteSagas(mintUrl: String): SagaRecoveryReport =
        forMint(mintUrl).recoverIncompleteSagas(mintUrl)

    override suspend fun sendEcashToken(
        amount: Long,
        memo: String?,
        p2pkPubkey: String?,
        mintUrl: String,
        unit: String,
        p2pkSigningKeys: List<String>,
    ): SendTokenResult =
        forMint(mintUrl).sendEcashToken(amount, memo, p2pkPubkey, mintUrl, unit, p2pkSigningKeys)

    override suspend fun receiveEcashToken(tokenString: String, p2pkSigningKeys: List<String>): Long =
        forTokenString(tokenString).receiveEcashToken(tokenString, p2pkSigningKeys)

    override suspend fun calculateReceiveFee(tokenString: String): Long =
        forTokenString(tokenString).calculateReceiveFee(tokenString)

    override suspend fun activeMintInputFeePpk(mintUrl: String): Long? =
        forMint(mintUrl).activeMintInputFeePpk(mintUrl)

    override suspend fun estimateCashuPaymentRequestFee(amountSats: Long, mintUrl: String): Long =
        forMint(mintUrl).estimateCashuPaymentRequestFee(amountSats, mintUrl)

    override suspend fun checkTokenSpendable(token: String, mintUrl: String): Boolean =
        forMint(mintUrl).checkTokenSpendable(token, mintUrl)

    override suspend fun listTransactions(unitsByMint: Map<String, List<String>>): List<WalletTransaction> {
        val (fediUnits, cdkUnits) = unitsByMint.entries.partition { FedimintSupport.isFederationKey(it.key) }
        val cdkRows = cdk.listTransactions(cdkUnits.associate { it.key to it.value })
        if (fediUnits.isEmpty()) return cdkRows
        val fediRows = runCatching { fedi.listTransactions(fediUnits.associate { it.key to it.value }) }
            .getOrDefault(emptyList())
        return cdkRows + fediRows
    }

    override suspend fun listPendingSendOperationIds(mintUrl: String, unit: String): List<String> =
        forMint(mintUrl).listPendingSendOperationIds(mintUrl, unit)

    override suspend fun checkPendingSendClaimed(mintUrl: String, operationId: String, unit: String): Boolean =
        forMint(mintUrl).checkPendingSendClaimed(mintUrl, operationId, unit)

    override suspend fun revokePendingSend(mintUrl: String, operationId: String, unit: String): Long =
        forMint(mintUrl).revokePendingSend(mintUrl, operationId, unit)

    override suspend fun mintUnissuedQuotes(mintUrl: String, unit: String): Long =
        forMint(mintUrl).mintUnissuedQuotes(mintUrl, unit)

    override suspend fun pendingSendTokenFromSaga(operationId: String): String? =
        runCatching { cdk.pendingSendTokenFromSaga(operationId) }.getOrNull()
            ?: runCatching { fedi.pendingSendTokenFromSaga(operationId) }.getOrNull()
}
