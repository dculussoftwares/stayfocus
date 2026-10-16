package com.dculus.stayfocused.core.sync

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.IOException

/** What the parent gets from `claimLinkToken`: the 6 digits both phones show and who is being linked. */
data class ClaimedLink(
    val code: String,
    val model: String,
    val deviceId: String,
    val expiresAtMillis: Long,
    /** True when the child had already allowed this link before this phone asked (the app was away). */
    val alreadyLinked: Boolean = false,
)

/** Why a handshake call did not go through, in terms the screens have words for. */
enum class LinkFailure {
    /** The token does not exist. */
    Invalid,
    Expired,

    /** Claimed by someone else, already answered, or declined. */
    Used,

    /** The child answered before any parent claimed. */
    NotClaimed,
    Network,
    Other,
}

sealed interface ClaimOutcome {
    data class Claimed(
        val link: ClaimedLink,
    ) : ClaimOutcome

    data class Failed(
        val reason: LinkFailure,
    ) : ClaimOutcome
}

/** Where the parent's claimed token is, as the Kids phone answers. */
enum class LinkAnswer { Pending, Linked, Declined }

/** What the Kids phone shows at Consent: the code the server made and the parent's first name. */
data class ConsentInfo(
    val code: String,
    val parentName: String,
    /** Epoch milliseconds after which the server refuses an answer; 0 when unknown. */
    val expiresAtMillis: Long = 0,
)

sealed interface ApproveOutcome {
    data class Linked(
        val parentUid: String,
        val deviceId: String,
        val parentName: String,
    ) : ApproveOutcome

    data object Declined : ApproveOutcome

    data class Failed(
        val reason: LinkFailure,
    ) : ApproveOutcome
}

/**
 * The pairing handshake on top of the `claimLinkToken` and `approveLink` Cloud Functions. The parent claims the
 * scanned token and waits; the child sees the same code and answers. Implementations never throw for expected failures.
 */
interface LinkHandshakeRepository {
    /** Parent: claims [token]. Calling again as the same parent only renames the device ([deviceName]). */
    suspend fun claim(
        token: String,
        deviceName: String?,
    ): ClaimOutcome

    /** Parent: withdraws the claim (codes do not match, or the parent left) so the child can no longer allow it. */
    suspend fun cancel(token: String)

    /** Parent: the child's answer to a claimed token. The flow fails when the listener is denied or lost. */
    fun observeAnswer(token: String): Flow<LinkAnswer>

    /** Child: emits once a parent has claimed [token]. The flow fails if the listener is denied or lost. */
    fun observeConsent(token: String): Flow<ConsentInfo>

    /** Child: allows ([approve]) or declines the link. */
    suspend fun answer(
        token: String,
        approve: Boolean,
    ): ApproveOutcome
}

internal const val CLAIM_LINK_FUNCTION = "claimLinkToken"
internal const val APPROVE_LINK_FUNCTION = "approveLink"
private const val TOKENS = "linkTokens"

internal class FirebaseLinkHandshakeRepository(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : LinkHandshakeRepository {
    // One-shot calls: each needs its own App Check token, which the server consumes to refuse a replay.
    private fun callable(name: String) =
        functions.getHttpsCallable(name, HttpsCallableOptions.Builder().setLimitedUseAppCheckTokens(true).build())

    @Suppress("TooGenericExceptionCaught")
    override suspend fun claim(
        token: String,
        deviceName: String?,
    ): ClaimOutcome =
        try {
            val request =
                buildMap<String, Any> {
                    put("token", token)
                    deviceName?.let { put("deviceName", it) }
                }
            val data = callable(CLAIM_LINK_FUNCTION).call(request).await().data as? Map<*, *>
            data?.toClaimedLink()?.let { ClaimOutcome.Claimed(it) } ?: ClaimOutcome.Failed(LinkFailure.Other)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Claiming the link token failed")
            ClaimOutcome.Failed(e.toLinkFailure())
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun cancel(token: String) {
        try {
            callable(CLAIM_LINK_FUNCTION).call(mapOf("token" to token, "cancel" to true)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort: the token still dies on its own within 5 minutes.
            Timber.w(e, "Withdrawing the link claim failed")
        }
    }

    override fun observeAnswer(token: String): Flow<LinkAnswer> =
        snapshots(token).map { snapshot ->
            when (snapshot.getString("status")) {
                "used" -> LinkAnswer.Linked
                "declined" -> LinkAnswer.Declined
                else -> LinkAnswer.Pending
            }
        }

    override fun observeConsent(token: String): Flow<ConsentInfo> =
        callbackFlow {
            val registration =
                firestore.collection(TOKENS).document(token).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else if (snapshot != null && snapshot.getString("status") == "claimed") {
                        val code = snapshot.getString("code")
                        if (code != null) {
                            trySend(
                                ConsentInfo(
                                    code,
                                    snapshot.getString("parentName").orEmpty(),
                                    snapshot.getTimestamp("expiresAt")?.toDate()?.time ?: 0L,
                                ),
                            )
                        }
                    }
                }
            awaitClose { registration.remove() }
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun answer(
        token: String,
        approve: Boolean,
    ): ApproveOutcome =
        try {
            val data =
                callable(APPROVE_LINK_FUNCTION)
                    .call(mapOf("token" to token, "approve" to approve))
                    .await()
                    .data as? Map<*, *>
            data?.toApproveOutcome() ?: ApproveOutcome.Failed(LinkFailure.Other)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Answering the link failed")
            ApproveOutcome.Failed(e.toLinkFailure())
        }

    private fun snapshots(token: String): Flow<DocumentSnapshot> =
        callbackFlow {
            val registration =
                firestore.collection(TOKENS).document(token).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else if (snapshot != null) {
                        trySend(snapshot)
                    }
                }
            awaitClose { registration.remove() }
        }
}

internal fun Map<*, *>.toClaimedLink(): ClaimedLink? {
    val code = this["code"] as? String
    val deviceId = this["deviceId"] as? String
    val expires = (this["expiresAtMillis"] as? Number)?.toLong()
    if (code == null || deviceId == null || expires == null) return null
    return ClaimedLink(code, this["model"] as? String ?: "", deviceId, expires, this["linked"] == true)
}

internal fun Map<*, *>.toApproveOutcome(): ApproveOutcome? =
    when (this["linked"]) {
        true -> {
            val parentUid = this["parentUid"] as? String
            val deviceId = this["deviceId"] as? String
            if (parentUid == null ||
                deviceId == null
            ) {
                null
            } else {
                ApproveOutcome.Linked(
                    parentUid,
                    deviceId,
                    this["parentName"] as? String ?: "",
                )
            }
        }

        false -> {
            ApproveOutcome.Declined
        }

        else -> {
            null
        }
    }

/** Maps the function's typed error (the `reason` in its details, see linkToken.ts in the functions source). */
internal fun Throwable.toLinkFailure(): LinkFailure =
    when {
        this is FirebaseFunctionsException -> toFunctionsFailure()
        isConnectivityFailure() -> LinkFailure.Network
        else -> LinkFailure.Other
    }

private fun FirebaseFunctionsException.toFunctionsFailure(): LinkFailure =
    reasonToFailure((details as? Map<*, *>)?.get("reason") as? String) ?: toTransportFailure()

/** The server's `reason` detail (see linkToken.ts in the functions source) as a failure; null when unknown. */
internal fun reasonToFailure(reason: String?): LinkFailure? =
    when (reason) {
        "invalid" -> LinkFailure.Invalid
        "expired" -> LinkFailure.Expired
        "used" -> LinkFailure.Used
        "not_claimed" -> LinkFailure.NotClaimed
        else -> null
    }

private fun FirebaseFunctionsException.toTransportFailure(): LinkFailure =
    when {
        code == FirebaseFunctionsException.Code.NOT_FOUND -> LinkFailure.Invalid

        code == FirebaseFunctionsException.Code.UNAVAILABLE ||
            code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ||
            isConnectivityFailure() -> LinkFailure.Network

        else -> LinkFailure.Other
    }

// The Functions SDK wraps a connectivity failure (no network, DNS) as INTERNAL with the IOException as cause.
private fun Throwable.isConnectivityFailure(): Boolean =
    generateSequence(this) { it.cause }.any { it is FirebaseNetworkException || it is IOException }

/** Used when the build has no Firebase configuration: linking is not available. */
internal object NoLinkHandshakeRepository : LinkHandshakeRepository {
    override suspend fun claim(
        token: String,
        deviceName: String?,
    ): ClaimOutcome = ClaimOutcome.Failed(LinkFailure.Other)

    override suspend fun cancel(token: String) = Unit

    override fun observeAnswer(token: String): Flow<LinkAnswer> = emptyFlow()

    override fun observeConsent(token: String): Flow<ConsentInfo> = emptyFlow()

    override suspend fun answer(
        token: String,
        approve: Boolean,
    ): ApproveOutcome = ApproveOutcome.Failed(LinkFailure.Other)
}

/** In-memory handshake for tests and previews: results are set by the test, answers pushed with [push]. */
class FakeLinkHandshakeRepository : LinkHandshakeRepository {
    var claimResult: ClaimOutcome =
        ClaimOutcome.Claimed(ClaimedLink("482913", "Pixel 7", "device-1", Long.MAX_VALUE))
    var answerResult: ApproveOutcome = ApproveOutcome.Linked("parent-1", "device-1", "Asha")

    /** When set, `answer` waits for it before returning (an answer still in flight). */
    var answerGate: CompletableDeferred<Unit>? = null

    /** Every `claim` call as (token, deviceName), oldest first. */
    val claims = mutableListOf<Pair<String, String?>>()

    /** Every `answer` call as (token, approve), oldest first. */
    val answers = mutableListOf<Pair<String, Boolean>>()

    private val parentAnswers = MutableStateFlow(LinkAnswer.Pending)
    private val consent = MutableSharedFlow<ConsentInfo>(replay = 1)

    override suspend fun claim(
        token: String,
        deviceName: String?,
    ): ClaimOutcome {
        claims += token to deviceName
        return claimResult
    }

    /** Every token the parent withdrew, oldest first. */
    val withdrawn = mutableListOf<String>()

    override suspend fun cancel(token: String) {
        withdrawn += token
    }

    override fun observeAnswer(token: String): Flow<LinkAnswer> = parentAnswers

    override fun observeConsent(token: String): Flow<ConsentInfo> = consent

    override suspend fun answer(
        token: String,
        approve: Boolean,
    ): ApproveOutcome {
        answers += token to approve
        answerGate?.await()
        return answerResult
    }

    /** Simulates the child answering, as the parent's listener would see it. */
    fun pushAnswer(answer: LinkAnswer) {
        parentAnswers.value = answer
    }

    /** Simulates the parent's claim reaching the child. */
    fun pushConsent(info: ConsentInfo) {
        consent.tryEmit(info)
    }
}
