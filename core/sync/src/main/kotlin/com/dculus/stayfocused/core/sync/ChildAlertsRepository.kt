package com.dculus.stayfocused.core.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** The permissions a Kids phone reports as lost, by their wire name in `alerts/{id}.permission`. */
object AlertPermission {
    const val ACCESSIBILITY = "accessibility"
    const val USAGE = "usage"
    const val OVERLAY = "overlay"
    const val NOTIFICATIONS = "notifications"
}

/** The Kids side of tamper alerts: `devices/{id}/alerts`, which `firestore.rules` let only this phone create. */
interface ChildAlertsRepository {
    /**
     * Opens a `permission_lost` alert for [permission] unless one is already open (not dismissed), so a permission that
     * stays off costs the parent one alert, not one per check. Throws on failure.
     */
    suspend fun raisePermissionLost(
        target: DeviceTarget,
        permission: String,
    )
}

internal class FirebaseChildAlertsRepository(
    private val firestore: FirebaseFirestore,
) : ChildAlertsRepository {
    override suspend fun raisePermissionLost(
        target: DeviceTarget,
        permission: String,
    ) {
        val alerts =
            firestore
                .collection("users")
                .document(target.parentUid)
                .collection("devices")
                .document(target.deviceId)
                .collection("alerts")
        val open =
            alerts
                .whereEqualTo("kind", KIND_PERMISSION_LOST)
                .whereEqualTo("permission", permission)
                .whereEqualTo("dismissed", false)
                .limit(1)
                .get()
                .await()
        if (!open.isEmpty) return
        alerts
            .add(
                mapOf(
                    "kind" to KIND_PERMISSION_LOST,
                    "permission" to permission,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "dismissed" to false,
                ),
            ).await()
    }

    private companion object {
        const val KIND_PERMISSION_LOST = "permission_lost"
    }
}

/** Used when the build has no Firebase configuration: nobody is told. */
internal object NoChildAlertsRepository : ChildAlertsRepository {
    override suspend fun raisePermissionLost(
        target: DeviceTarget,
        permission: String,
    ) = Unit
}

/** In-memory repository for tests that mirrors the one-open-alert-per-permission rule. */
class FakeChildAlertsRepository : ChildAlertsRepository {
    /** Permissions with an open alert, in the order they were raised. */
    val open = mutableListOf<String>()

    /** Every call, including the ones that found an alert already open. */
    val calls = mutableListOf<String>()

    /** Set to make every write throw (simulates offline). */
    var failing = false

    override suspend fun raisePermissionLost(
        target: DeviceTarget,
        permission: String,
    ) {
        calls += permission
        if (failing) error("offline")
        if (permission !in open) open += permission
    }

    /** The parent dismissed the alert. */
    fun dismiss(permission: String) {
        open -= permission
    }
}
