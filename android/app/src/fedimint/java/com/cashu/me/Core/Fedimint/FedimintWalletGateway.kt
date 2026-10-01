package com.cashu.me.Core.Fedimint

import android.content.Context
import com.cashu.me.Core.CDK.CdkGatewayUnavailable
import com.cashu.me.Core.CDK.CdkWalletGateway
import com.cashu.me.Core.CDK.MeltConfirmation
import com.cashu.me.Core.CDK.NfcReceiveReceipt
import com.cashu.me.Core.CDK.ForeignNfcSettlement
import com.cashu.me.Core.CDK.ReceiveRecoveryCandidate
import com.cashu.me.Core.CDK.SagaRecoveryReport
import com.cashu.me.Core.CDK.WalletAccountReference
import com.cashu.me.Core.NPCQuote
import com.cashu.me.Models.FederationDetails
import com.cashu.me.Models.FederationGuardian
import com.cashu.me.Models.FederationNetwork
import com.cashu.me.Models.FederationState
import com.cashu.me.Models.GuardianHealth
import com.cashu.me.Models.MeltPaymentResult
import com.cashu.me.Models.MeltQuoteInfo
import com.cashu.me.Models.MeltQuoteState
import com.cashu.me.Models.MintInfo
import com.cashu.me.Models.MintQuoteInfo
import com.cashu.me.Models.MintQuoteState
import com.cashu.me.Models.PaymentMethodKind
import com.cashu.me.Models.RestoreMintResult
import com.cashu.me.Models.SendTokenResult
import com.cashu.me.Models.TransactionKind
import com.cashu.me.Models.TransactionStatus
import com.cashu.me.Models.TransactionType
import com.cashu.me.Models.WalletTransaction
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.fedimint.sdk.ActivityItem
import org.fedimint.sdk.ActivityStatus
import org.fedimint.sdk.Direction
import org.fedimint.sdk.EcashReceiveState
import org.fedimint.sdk.EcashSendState
import org.fedimint.sdk.ErrorCode
import org.fedimint.sdk.Federation
import org.fedimint.sdk.FederationStatus
import org.fedimint.sdk.InviteCode
import org.fedimint.sdk.LnQuote
import org.fedimint.sdk.LnReceiveState
import org.fedimint.sdk.LnSendState
import org.fedimint.sdk.Network
import org.fedimint.sdk.Notes
import org.fedimint.sdk.OperationKind
import org.fedimint.sdk.Sdk
import org.fedimint.sdk.createFedimintSdk
import org.fedimint.sdk.Exception as SdkException

/**
 * Fedimint backend behind the same gateway seam the Cashu wallet uses.
 *
 * Federations are mints keyed `fedimint:<federationId>`, balances are reported
 * in whole sats (the SDK counts msats), and quote ids carry an `fm-` prefix so
 * [com.cashu.me.Core.CDK.CompositeWalletGateway] can route them. The Fedimint
 * client keeps its own seed (created and persisted by the SDK under `filesDir`).
 */
class FedimintWalletGateway(context: Context) : CdkWalletGateway {
    private val dataDir = File(context.applicationContext.filesDir, "fedimint").apply { mkdirs() }
    private val sdkLock = Mutex()
    private var sdk: Sdk? = null
    private val meltQuotes = ConcurrentHashMap<String, LnQuote>()

    private suspend fun sdk(): Sdk = sdkLock.withLock {
        sdk ?: createFedimintSdk(dataDir.path, null).also { sdk = it }
    }

    private suspend fun federation(key: String): Federation {
        val sdk = sdk()
        val id = FedimintSupport.federationId(key)
        return runCatching { sdk.federation(id) }.getOrNull()
            ?: sdk.reopenFederation(id)
    }

    private fun msatsToSats(msats: ULong): Long = (msats / 1000UL).toLong()
    private fun msatsToSatsCeil(msats: ULong): Long = ((msats + 999UL) / 1000UL).toLong()

    private suspend fun infoFor(federation: Federation): MintInfo {
        val hasLightning = federation.lightning() != null
        val rails = if (hasLightning) listOf(PaymentMethodKind.Bolt11) else emptyList()
        val config = federation.meta().configMetadata()
        val meta = FedimintGuardianApi.mergeMeta(
            config = config,
            external = externalMeta(federation.id(), config),
            sdkMerged = liveDetails[federation.id()]?.meta.orEmpty(),
        )
        return MintInfo(
            url = FedimintSupport.keyFor(federation.id()),
            name = federation.name()?.takeIf { it.isNotBlank() } ?: "Federation",
            iconUrl = (meta["federation_icon_url"] ?: meta["fedi:federation_icon_url"])?.takeIf { it.isNotBlank() },
            balance = msatsToSats(federation.balance()),
            units = listOf("sat"),
            mintUnits = listOf("sat"),
            supportedMintMethods = rails,
            supportedMeltMethods = rails,
        )
    }

    // ---- lifecycle / identity ------------------------------------------------

    override suspend fun initializeLogging(level: String) = Unit
    override suspend fun generateMnemonic(): String = unavailable("mnemonic")
    override suspend fun mnemonicEntropy(mnemonic: String): ByteArray = unavailable("mnemonic")
    override suspend fun validateMnemonic(mnemonic: String): Boolean = unavailable("mnemonic")
    override suspend fun openWalletRepository(mnemonic: String, databasePath: String) = Unit
    override suspend fun closeWalletRepository() = Unit
    override suspend fun hasWallets(): Boolean = sdk().storedFederations().isNotEmpty()
    override suspend fun backupMints(relays: List<String>, client: String) = unavailable("mint backup")
    override suspend fun fetchMintBackup(relays: List<String>, timeoutSecs: ULong): List<String> =
        unavailable("mint backup")

    override suspend fun ensureWallet(mintUrl: String, unit: String) {
        federation(mintUrl)
    }

    override suspend fun joinFederation(invite: String): MintInfo {
        val code = try {
            InviteCode.parse(invite)
        } catch (error: SdkException) {
            throw IllegalArgumentException("That doesn't look like a federation invite code.")
        }
        val sdk = sdk()
        val joined = try {
            sdk.join(code)
        } catch (error: SdkException) {
            if (error.code() == ErrorCode.ALREADY_JOINED) {
                federation(FedimintSupport.keyFor(code.federationId()))
            } else {
                throw IllegalStateException(friendly(error), error)
            }
        }
        return infoFor(joined)
    }

    override suspend fun removeWalletIfSingleUnit(mintUrl: String): Boolean {
        val id = FedimintSupport.federationId(mintUrl)
        try {
            sdk().forgetFederation(id)
        } catch (error: SdkException) {
            when (error.code()) {
                ErrorCode.BALANCE_NOT_EMPTY, ErrorCode.PENDING_OPERATIONS ->
                    throw IllegalStateException("Spend or send this federation's balance before removing it.")
                else -> throw IllegalStateException(friendly(error), error)
            }
        }
        return true
    }

    override suspend fun fetchMintInfo(mintUrl: String): MintInfo? = infoFor(federation(mintUrl))

    // ---- federation details ---------------------------------------------------

    override suspend fun federationDetails(mintUrl: String, live: Boolean): FederationDetails =
        withContext(Dispatchers.IO) {
            val sdk = sdk()
            val id = FedimintSupport.federationId(mintUrl)
            // A quarantined or closed federation has no live handle; its stored record still names it.
            val federation = try {
                federation(mintUrl)
            } catch (error: SdkException) {
                null
            }
            val local = federation?.let { localDetails(sdk, it) } ?: storedDetails(sdk, id)
            if (!live) return@withContext local.withLive(liveDetails[id], fresh = false)
            if (federation == null) {
                throw IllegalStateException((local.state as? FederationState.Quarantined)?.reason ?: "This federation isn't running.")
            }
            val fetched = withTimeoutOrNull(LIVE_DETAILS_TIMEOUT_MILLIS) { fetchLiveDetails(sdk, federation) }
                ?: throw IllegalStateException("Couldn't reach the federation.")
            liveDetails[id] = fetched
            local.withLive(fetched, fresh = true)
        }

    /** Identity, capabilities, status, invite and configuration metadata: all local reads. */
    private fun localDetails(sdk: Sdk, federation: Federation): FederationDetails {
        val id = federation.id()
        val invite = federation.inviteCode().display()
        val capabilities = federation.capabilities()
        return FederationDetails(
            federationId = id,
            name = federation.name()?.takeIf { it.isNotBlank() },
            network = federation.network().toModel(),
            state = sdk.federationStatus(id)?.toModel() ?: FederationState.Running,
            supportsEcash = capabilities.ecash,
            supportsLightning = capabilities.lightning,
            supportsOnchain = capabilities.onchain,
            inviteCode = invite,
            inviteGuardians = FedimintInviteCode.decode(invite)?.guardians.orEmpty(),
            meta = federation.meta().configMetadata(),
        )
    }

    private fun storedDetails(sdk: Sdk, id: String): FederationDetails {
        val stored = sdk.storedFederations().firstOrNull { it.id == id }
            ?: throw IllegalStateException("This federation is no longer in the wallet.")
        return FederationDetails(
            federationId = id,
            name = stored.name?.takeIf { it.isNotBlank() },
            network = stored.network.toModel(),
            state = stored.status.toModel(),
            supportsEcash = false,
            supportsLightning = false,
            supportsOnchain = false,
            inviteCode = "",
            inviteGuardians = emptyList(),
        )
    }

    /**
     * The guardians' view: the config (guardian count, modules) via a preview of
     * our own invite, and the merged metadata with its consensus revision. The
     * preview is the reachability gate; metadata falls back to the config's.
     */
    private suspend fun fetchLiveDetails(sdk: Sdk, federation: Federation): LiveDetails = coroutineScope {
        val meta = federation.meta()
        val configMeta = meta.configMetadata()
        val merged = async { orNull { meta.all() } }
        val revision = async { orNull { meta.consensusMetadata()?.revision?.toLong() } }
        // Beyond the SDK: the guardians' own API for names and health, and the
        // federation's external meta (icon, welcome message, limits). Both are
        // best effort and bounded, so they never decide reachability.
        val probe = async {
            orNull {
                withTimeoutOrNull(GUARDIAN_PROBE_BUDGET_MILLIS) {
                    guardianApi.probe(FedimintInviteCode.decode(federation.inviteCode().display())?.guardians.orEmpty())
                }
            }
        }
        val external = async { externalMeta(federation.id(), configMeta) }
        val preview = try {
            sdk.preview(federation.inviteCode())
        } catch (error: SdkException) {
            throw IllegalStateException(friendly(error), error)
        }
        val guardians = probe.await()
        LiveDetails(
            guardianCount = preview.guardians.toInt(),
            modules = preview.modules,
            meta = merged.await() ?: preview.meta,
            metaRevision = revision.await(),
            externalMeta = external.await(),
            roster = guardians?.guardians.orEmpty(),
            sessionCount = guardians?.sessionCount,
        )
    }

    /**
     * [fresh] is false for the instant snapshot: names and addresses carry
     * over from the last read, but its health is history, so it is cleared.
     */
    private fun FederationDetails.withLive(live: LiveDetails?, fresh: Boolean): FederationDetails {
        if (live == null) {
            val external = externalMetaCache[federationId] ?: return this
            return copy(meta = FedimintGuardianApi.mergeMeta(meta, external, emptyMap()))
        }
        return copy(
            guardianCount = live.guardianCount,
            modules = live.modules,
            meta = FedimintGuardianApi.mergeMeta(config = meta, external = live.externalMeta, sdkMerged = live.meta),
            metaRevision = live.metaRevision,
            guardianRoster = if (fresh) live.roster else live.roster.map { it.copy(health = GuardianHealth.Unknown) },
            sessionCount = live.sessionCount.takeIf { fresh },
        )
    }

    /** The federation's external meta, fetched once per process; empty when it publishes none. */
    private suspend fun externalMeta(federationId: String, configMeta: Map<String, String>): Map<String, String> {
        externalMetaCache[federationId]?.let { return it }
        val url = FedimintGuardianApi.externalMetaUrl(configMeta) ?: return emptyMap()
        return orNull { guardianApi.externalMeta(url, federationId) }
            ?.also { externalMetaCache[federationId] = it }
            .orEmpty()
    }

    private val guardianApi = FedimintGuardianApi.Client()
    private val externalMetaCache = ConcurrentHashMap<String, Map<String, String>>()

    private suspend fun <T> orNull(block: suspend () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        null
    }

    /** Last successful live read per federation id, so a reopened screen fills in immediately. */
    private val liveDetails = ConcurrentHashMap<String, LiveDetails>()

    private data class LiveDetails(
        val guardianCount: Int,
        val modules: List<String>,
        val meta: Map<String, String>,
        val metaRevision: Long?,
        val externalMeta: Map<String, String>,
        val roster: List<FederationGuardian>,
        val sessionCount: Long?,
    )

    private fun FederationStatus.toModel(): FederationState = when (this) {
        is FederationStatus.Running -> FederationState.Running
        is FederationStatus.Recovering -> FederationState.Recovering
        is FederationStatus.Quarantined -> FederationState.Quarantined(diagnostic.message)
        else -> FederationState.Closed
    }

    private fun Network.toModel(): FederationNetwork = when (this) {
        Network.BITCOIN -> FederationNetwork.Bitcoin
        Network.TESTNET -> FederationNetwork.Testnet
        Network.TESTNET4 -> FederationNetwork.Testnet4
        Network.SIGNET -> FederationNetwork.Signet
        Network.REGTEST -> FederationNetwork.Regtest
    }

    override suspend fun restoreMint(mintUrl: String): RestoreMintResult =
        RestoreMintResult(mintUrl = mintUrl, mintName = "Federation", spent = 0, unspent = 0, pending = 0)

    // ---- balances -----------------------------------------------------------

    override suspend fun storedAccounts(): List<WalletAccountReference> =
        sdk().storedFederations().map { WalletAccountReference(FedimintSupport.keyFor(it.id), "sat") }

    override suspend fun storedAccountBalance(account: WalletAccountReference): Long =
        msatsToSats(federation(account.mintUrl).balance())

    override suspend fun totalBalance(mintUrl: String): Long = msatsToSats(federation(mintUrl).balance())
    override suspend fun unitBalance(mintUrl: String, unit: String): Long = totalBalance(mintUrl)
    override suspend fun unitBalanceIfExists(mintUrl: String, unit: String): Long? = totalBalance(mintUrl)

    // ---- lightning receive ("mint") -------------------------------------------

    override suspend fun createMintQuote(
        amount: Long?,
        method: PaymentMethodKind,
        mintUrl: String,
        unit: String,
        description: String?,
    ): MintQuoteInfo {
        if (method != PaymentMethodKind.Bolt11) throw CdkGatewayUnavailable("Federations only support Lightning (BOLT11).")
        val sats = amount?.takeIf { it > 0 } ?: throw IllegalArgumentException("Enter an amount.")
        val federation = federation(mintUrl)
        val lightning = federation.lightning() ?: throw CdkGatewayUnavailable("This federation has no Lightning module.")
        val handle = try {
            lightning.receive((sats * 1000).toULong(), description.orEmpty())
        } catch (error: SdkException) {
            throw IllegalStateException(friendly(error), error)
        }
        val details = handle.operation.details()
        return MintQuoteInfo(
            id = quoteId(FedimintSupport.MINT_QUOTE_PREFIX, federation.id(), handle.operation.id()),
            request = handle.invoice,
            amount = sats,
            paymentMethod = PaymentMethodKind.Bolt11,
            state = MintQuoteState.Unpaid,
            expiryEpochSeconds = (details.expiresAt / 1000UL).toLong(),
            mintUrl = mintUrl,
            unit = "sat",
            description = description,
        )
    }

    override suspend fun checkMintQuote(quoteId: String): MintQuoteInfo {
        val (federationId, operationId) = parseQuoteId(quoteId, FedimintSupport.MINT_QUOTE_PREFIX)
        val key = FedimintSupport.keyFor(federationId)
        val operation = federation(key).operation(operationId)?.asLnReceive()
            ?: throw IllegalStateException("Unknown Lightning invoice.")
        val details = operation.details()
        val sats = msatsToSats(details.requestedAmount)
        val state = operation.state()
        val quoteState = when (state) {
            is LnReceiveState.Claimed -> MintQuoteState.Issued
            is LnReceiveState.Funded -> MintQuoteState.Paid
            is LnReceiveState.Expired, is LnReceiveState.Canceled, is LnReceiveState.Failed -> MintQuoteState.Failed
            else -> MintQuoteState.Unpaid
        }
        val settled = quoteState == MintQuoteState.Issued
        return MintQuoteInfo(
            id = quoteId,
            request = details.invoice,
            amount = sats,
            paymentMethod = PaymentMethodKind.Bolt11,
            state = quoteState,
            expiryEpochSeconds = (details.expiresAt / 1000UL).toLong(),
            mintUrl = key,
            amountPaid = if (settled || quoteState == MintQuoteState.Paid) sats else 0,
            amountIssued = if (settled) sats else 0,
            updatedAtEpochSeconds = System.currentTimeMillis() / 1000,
            unit = "sat",
            description = details.description.takeIf { it.isNotBlank() },
        )
    }

    override fun subscribeToMintQuote(quoteId: String, mayRefresh: () -> Boolean): Flow<MintQuoteInfo> = flow {
        while (true) {
            val quote = checkMintQuote(quoteId)
            emit(quote)
            if (quote.state == MintQuoteState.Issued || quote.state == MintQuoteState.Failed) return@flow
            delay(POLL_MILLIS)
        }
    }

    override suspend fun listUnissuedMintQuotes(): List<MintQuoteInfo> = emptyList()

    /** Fedimint claims automatically; wait for the ecash to land and report the credited amount. */
    override suspend fun mintTokens(quoteId: String): Long {
        val (federationId, operationId) = parseQuoteId(quoteId, FedimintSupport.MINT_QUOTE_PREFIX)
        val operation = federation(FedimintSupport.keyFor(federationId)).operation(operationId)?.asLnReceive()
            ?: throw IllegalStateException("Unknown Lightning invoice.")
        return when (operation.awaitFinal()) {
            is LnReceiveState.Claimed -> msatsToSats(operation.details().netCredit)
            else -> throw IllegalStateException("The invoice was not paid.")
        }
    }

    override suspend fun mintNPCQuote(quote: NPCQuote, p2pkPubkey: String?): Long = unavailable("npub.cash")

    // ---- lightning send ("melt") ---------------------------------------------

    override suspend fun createMeltQuote(request: String, amountSats: Long?, preferredMintURL: String?): MeltQuoteInfo {
        val key = preferredMintURL?.takeIf(FedimintSupport::isFederationKey)
            ?: throw CdkGatewayUnavailable("No federation selected.")
        val federation = federation(key)
        val lightning = federation.lightning() ?: throw CdkGatewayUnavailable("This federation has no Lightning module.")
        val quote = try {
            lightning.quote(request.trim().removePrefix("lightning:").removePrefix("LIGHTNING:"))
        } catch (error: SdkException) {
            throw IllegalStateException(friendly(error), error)
        }
        val id = quoteId(FedimintSupport.MELT_QUOTE_PREFIX, federation.id(), UUID.randomUUID().toString().replace("-", ""))
        meltQuotes[id] = quote
        return MeltQuoteInfo(
            id = id,
            mintUrl = key,
            amount = msatsToSats(quote.invoiceAmount()),
            feeReserve = msatsToSatsCeil(quote.fee()),
            paymentMethod = PaymentMethodKind.Bolt11,
            state = MeltQuoteState.Unpaid,
            expiryEpochSeconds = (quote.expiresAt() / 1000UL).toLong(),
            request = request,
        )
    }

    override suspend fun listMeltQuotes(): List<MeltQuoteInfo> = emptyList()

    override suspend fun meltTokens(quoteId: String, mintUrl: String?): MeltConfirmation {
        val quote = meltQuotes.remove(quoteId)
            ?: throw IllegalStateException("This payment quote expired. Please try again.")
        val (federationId, _) = parseQuoteId(quoteId, FedimintSupport.MELT_QUOTE_PREFIX)
        val key = FedimintSupport.keyFor(federationId)
        val lightning = federation(key).lightning() ?: throw CdkGatewayUnavailable("This federation has no Lightning module.")
        val operation = try {
            lightning.send(quote)
        } catch (error: SdkException) {
            throw IllegalStateException(friendly(error), error)
        }
        return when (val state = operation.awaitFinal()) {
            is LnSendState.Success -> MeltConfirmation(
                result = MeltPaymentResult(
                    preimage = state.preimage,
                    amount = msatsToSats(quote.invoiceAmount()),
                    feePaid = msatsToSatsCeil(state.fee),
                    mintUrl = key,
                    paymentMethod = PaymentMethodKind.Bolt11,
                ),
                pendingMelt = null,
            )
            is LnSendState.Failed -> throw IllegalStateException(state.reason)
            else -> throw IllegalStateException("The payment did not complete; your funds were refunded.")
        }
    }

    override suspend fun checkMeltQuoteStatus(quoteId: String, mintUrl: String?): MeltQuoteInfo =
        throw CdkGatewayUnavailable("Federation payments settle immediately.")

    override suspend fun recoverIncompleteSagas(mintUrl: String): SagaRecoveryReport = SagaRecoveryReport(0, 0, 0, 0)
    override suspend fun receiveRecoveryCandidates(): List<ReceiveRecoveryCandidate> = emptyList()
    override suspend fun recoverReceiveAccount(candidate: ReceiveRecoveryCandidate): SagaRecoveryReport =
        SagaRecoveryReport(0, 0, 0, 0)

    // ---- ecash --------------------------------------------------------------

    override suspend fun sendEcashToken(
        amount: Long,
        memo: String?,
        p2pkPubkey: String?,
        mintUrl: String,
        unit: String,
        p2pkSigningKeys: List<String>,
    ): SendTokenResult {
        if (p2pkPubkey != null) throw CdkGatewayUnavailable("Federations don't support locked ecash.")
        val ecash = federation(mintUrl).ecash() ?: throw CdkGatewayUnavailable("This federation has no ecash module.")
        try {
            val quote = ecash.quote((amount * 1000).toULong())
            val sent = ecash.send(quote)
            return SendTokenResult(
                token = sent.notes.display(),
                fee = msatsToSatsCeil(quote.fee()),
                transactionId = sent.operation.id(),
            )
        } catch (error: SdkException) {
            throw IllegalStateException(friendly(error), error)
        }
    }

    /** Federation sends charge the sender a fee on top, so "Max" is the largest amount that still quotes. */
    override suspend fun maxSendableEcash(mintUrl: String, unit: String): Long? {
        val federation = federation(mintUrl)
        val ecash = federation.ecash() ?: return null
        var amount = msatsToSats(federation.balance())
        var attempts = 0
        while (amount > 0 && attempts++ < MAX_QUOTE_ATTEMPTS) {
            try {
                ecash.quote((amount * 1000).toULong()).close()
                return amount
            } catch (error: SdkException) {
                if (error.code() != ErrorCode.INSUFFICIENT_BALANCE) return null
                amount -= 1
            }
        }
        return 0
    }

    override suspend fun receiveEcashToken(tokenString: String, p2pkSigningKeys: List<String>): Long {
        val notes = try {
            Notes.parse(FedimintSupport.extractNotes(tokenString) ?: tokenString.trim())
        } catch (error: SdkException) {
            throw IllegalArgumentException("Those aren't valid Fedimint ecash notes.")
        }
        val sdk = sdk()
        var lastError: Throwable? = null
        for (info in sdk.storedFederations()) {
            val federation = federation(FedimintSupport.keyFor(info.id))
            val ecash = federation.ecash() ?: continue
            try {
                val operation = ecash.receive(notes)
                return when (val state = operation.awaitFinal()) {
                    is EcashReceiveState.Done -> msatsToSats(operation.details().netCredit)
                    is EcashReceiveState.Failed -> throw IllegalStateException(state.reason)
                    else -> throw IllegalStateException("The notes were not redeemed.")
                }
            } catch (error: SdkException) {
                // Notes belong to exactly one federation; try the next joined one.
                lastError = error
            }
        }
        throw IllegalStateException(
            "Join the federation these notes belong to first." +
                (lastError?.let { " (${(it as? SdkException)?.reason() ?: it.message})" } ?: ""),
        )
    }

    override suspend fun receiveNfcEcashToken(tokenString: String, p2pkSigningKeys: List<String>): NfcReceiveReceipt =
        unavailable("NFC")

    override suspend fun settleForeignNfcToken(tokenString: String, settlementMintUrl: String): ForeignNfcSettlement =
        unavailable("NFC")

    override suspend fun calculateReceiveFee(tokenString: String): Long = 0
    override suspend fun activeMintInputFeePpk(mintUrl: String): Long? = null
    override suspend fun estimateCashuPaymentRequestFee(amountSats: Long, mintUrl: String): Long =
        unavailable("Cashu payment requests")

    /**
     * The app reads `true` as "already spent". Notes carry no operation id, so a
     * bare token can't be checked; report unspent and let the operation-based
     * checks ([checkPendingSendClaimed]) decide whether a send was redeemed.
     */
    override suspend fun checkTokenSpendable(token: String, mintUrl: String): Boolean = false

    override suspend fun payCashuPaymentRequest(encoded: String, customAmountSats: Long?, preferredMintURL: String?) =
        unavailable("Cashu payment requests")

    // ---- pending sends ------------------------------------------------------

    override suspend fun listPendingSendOperationIds(mintUrl: String, unit: String): List<String> =
        federation(mintUrl).activity(null, 100.toUShort()).items
            .filter { it.kind == OperationKind.ECASH_SEND && it.status == ActivityStatus.PENDING }
            .map { it.operationId }

    override suspend fun checkPendingSendClaimed(mintUrl: String, operationId: String, unit: String): Boolean {
        val operation = federation(mintUrl).operation(operationId)?.asEcashSend() ?: return false
        return operation.state() == EcashSendState.REDEEMED
    }

    override suspend fun revokePendingSend(mintUrl: String, operationId: String, unit: String): Long {
        val federation = federation(mintUrl)
        val operation = federation.operation(operationId)?.asEcashSend()
            ?: throw IllegalStateException("Unknown send.")
        val before = federation.balance()
        operation.requestCancel()
        if (operation.awaitFinal() != EcashSendState.CANCELED) {
            throw IllegalStateException("The notes were already redeemed.")
        }
        val after = federation.balance()
        return msatsToSats(if (after > before) after - before else 0UL)
    }

    override suspend fun mintUnissuedQuotes(mintUrl: String, unit: String): Long = 0
    override suspend fun pendingSendTokenFromSaga(operationId: String): String? = null

    // ---- history --------------------------------------------------------------

    override suspend fun listTransactions(unitsByMint: Map<String, List<String>>): List<WalletTransaction> =
        unitsByMint.keys.filter(FedimintSupport::isFederationKey).flatMap { key ->
            val federation = federation(key)
            val federationId = federation.id()
            federation.activity(null, 100.toUShort()).items.mapNotNull { item ->
                val kind = when (item.kind) {
                    OperationKind.ECASH_SEND, OperationKind.ECASH_RECEIVE -> TransactionKind.Ecash
                    OperationKind.LN_SEND, OperationKind.LN_RECEIVE -> TransactionKind.Lightning
                    OperationKind.ONCHAIN_SEND, OperationKind.ONCHAIN_RECEIVE -> TransactionKind.Onchain
                    else -> return@mapNotNull null
                }
                WalletTransaction(
                    id = item.operationId,
                    amount = msatsToSats(item.amount ?: 0UL),
                    type = if (item.direction == Direction.INCOMING) TransactionType.Incoming else TransactionType.Outgoing,
                    kind = kind,
                    dateEpochMillis = item.time.toLong(),
                    status = when (item.status) {
                        ActivityStatus.PENDING -> pendingStatus(federation, item)
                        ActivityStatus.SUCCESS -> TransactionStatus.Completed
                        else -> TransactionStatus.Failed
                    },
                    mintUrl = key,
                    fee = item.fee?.let { msatsToSatsCeil(it) } ?: 0,
                    unit = "sat",
                    sagaId = item.operationId.takeIf { item.kind == OperationKind.ECASH_SEND },
                    quoteId = if (item.kind == OperationKind.LN_RECEIVE) {
                        quoteId(FedimintSupport.MINT_QUOTE_PREFIX, federationId, item.operationId)
                    } else {
                        null
                    },
                    paymentMethod = if (kind == TransactionKind.Lightning) PaymentMethodKind.Bolt11 else null,
                )
            }
        }

    // ---- helpers --------------------------------------------------------------

    /**
     * The activity feed can lag the operation, so a pending ecash send asks the
     * operation itself whether the receiver has redeemed (or the send was cancelled).
     */
    private suspend fun pendingStatus(federation: Federation, item: ActivityItem): TransactionStatus {
        if (item.kind != OperationKind.ECASH_SEND) return TransactionStatus.Pending
        val state = runCatching { federation.operation(item.operationId)?.asEcashSend()?.state() }.getOrNull()
        return when (state) {
            EcashSendState.REDEEMED -> TransactionStatus.Completed
            EcashSendState.CANCELED -> TransactionStatus.Failed
            else -> TransactionStatus.Pending
        }
    }

    private fun quoteId(prefix: String, federationId: String, operationId: String) =
        "$prefix$federationId-$operationId"

    private fun parseQuoteId(quoteId: String, prefix: String): Pair<String, String> {
        val body = quoteId.removePrefix(prefix)
        val split = body.indexOf('-')
        require(split > 0) { "Malformed federation quote id." }
        return body.substring(0, split) to body.substring(split + 1)
    }

    private fun friendly(error: SdkException): String = when (error.code()) {
        ErrorCode.INSUFFICIENT_BALANCE -> "Not enough balance in this federation."
        ErrorCode.FEDERATION_UNREACHABLE, ErrorCode.TIMEOUT -> "Couldn't reach the federation."
        ErrorCode.INVALID_INPUT -> "That isn't valid for this federation."
        ErrorCode.UNSUPPORTED_FEDERATION -> "This app can't work with that federation."
        ErrorCode.GATEWAY_UNAVAILABLE -> "No Lightning gateway is available right now."
        ErrorCode.NETWORK_MISMATCH -> "That payment is for a different Bitcoin network."
        else -> error.reason()
    }

    private fun unavailable(what: String): Nothing =
        throw CdkGatewayUnavailable("$what is not supported for federations.")

    private companion object {
        const val POLL_MILLIS = 2_000L
        const val MAX_QUOTE_ATTEMPTS = 200
        const val LIVE_DETAILS_TIMEOUT_MILLIS = 15_000L
        const val GUARDIAN_PROBE_BUDGET_MILLIS = 11_000L
    }
}
