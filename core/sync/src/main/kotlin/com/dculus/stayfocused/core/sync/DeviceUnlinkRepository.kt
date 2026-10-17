package com.dculus.stayfocused.core.sync

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/** How an unlink ended. */
sealed interface UnlinkResult {
    /** The device and everything under it is gone. */
    data object Unlinked : UnlinkResult

    /** No connection or the server could not be reached: nothing was changed, trying again can work. */
    data object Network : UnlinkResult

    data object Failed : UnlinkResult
}

/**
 * Unlinks a child's phone through the `unlinkDevice` Cloud Function, which deletes `users/{parent}/devices/{id}` with
 * everything under it. The child calls it for itself; the parent's Remote screen uses the same function for a device
 * it owns. Implementations never throw for expected failures.
 */
interface DeviceUnlinkRepository {
    /**
     * Unlinks [deviceId]. A child passes the [parentUid] it is linked to; a parent leaves it null (their own account
     * owns the device).
     */
    suspend fun unlink(
        deviceId: String,
        parentUid: String? = null,
    ): UnlinkResult
}

internal const val UNLINK_DEVICE_FUNCTION = "unlinkDevice"

internal class FirebaseDeviceUnlinkRepository(
    private val functions: FirebaseFunctions,
) : DeviceUnlinkRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun unlink(
        deviceId: String,
        parentUid: String?,
    ): UnlinkResult =
        try {
            val request =
                buildMap<String, Any> {
                    put("deviceId", deviceId)
                    parentUid?.let { put("parentUid", it) }
                }
            functions.getHttpsCallable(UNLINK_DEVICE_FUNCTION).call(request).await()
            UnlinkResult.Unlinked
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Unlinking the device failed")
            e.toUnlinkResult()
        }
}

/**
 * A refusal is a failure: it can mean the device belongs to someone else or the identity is wrong, and neither is
 * "unlinked". A device that is already gone is noticed by the Kids home through [KidsHomeRepository.observeLink].
 */
internal fun Throwable.toUnlinkResult(): UnlinkResult =
    if (toLinkFailure() == LinkFailure.Network) UnlinkResult.Network else UnlinkResult.Failed

/** Used when the build has no Firebase configuration: there is nothing to unlink from. */
internal object NoDeviceUnlinkRepository : DeviceUnlinkRepository {
    override suspend fun unlink(
        deviceId: String,
        parentUid: String?,
    ): UnlinkResult = UnlinkResult.Failed
}

/** In-memory repository for tests: returns [result] and records each call as (deviceId, parentUid). */
class FakeDeviceUnlinkRepository : DeviceUnlinkRepository {
    var result: UnlinkResult = UnlinkResult.Unlinked
    val calls = mutableListOf<Pair<String, String?>>()

    /** When set, `unlink` waits for it before returning (a call still in flight). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun unlink(
        deviceId: String,
        parentUid: String?,
    ): UnlinkResult {
        calls += deviceId to parentUid
        gate?.await()
        return result
    }
}
