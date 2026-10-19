package com.dculus.stayfocused.core.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Where a Kids phone reports to: `users/{parentUid}/devices/{deviceId}`. */
data class DeviceTarget(
    val parentUid: String,
    val deviceId: String,
)

/** What the parent sees of the child's phone right now. */
data class ChildStatus(
    /** 0..100, or null when unknown. */
    val battery: Int?,
    val charging: Boolean,
    /** Label (not package name) of the app in front, or null. */
    val currentApp: String?,
    val online: Boolean,
    /** Android release (`Build.VERSION.RELEASE`), or null when unknown. */
    val osVersion: String? = null,
)

data class UsageAppEntry(
    val pkg: String,
    val label: String,
    val mins: Int,
    val opens: Int,
)

/** One day of usage as stored in `usage/{yyyy-MM-dd}`. */
data class UsageSummary(
    /** `yyyy-MM-dd`. */
    val date: String,
    val totalMins: Int,
    val apps: List<UsageAppEntry>,
    /** Minutes of use per local hour of the day (24 entries, 0..60); null when not known. */
    val hourly: List<Int>? = null,
)

/** The Kids side of monitoring: the writes the parent's remote device screen reads. All of them throw on failure. */
interface ChildStatusRepository {
    /** Updates the status fields of the device doc; `lastSeen` is the server time. */
    suspend fun writeStatus(
        target: DeviceTarget,
        status: ChildStatus,
    )

    suspend fun writeUsage(
        target: DeviceTarget,
        summary: UsageSummary,
    )

    /** Makes `devices/{id}/apps` match [apps] (package to label): writes new or renamed ones, deletes the rest. */
    suspend fun syncApps(
        target: DeviceTarget,
        apps: Map<String, String>,
    )
}

internal object StatusLimits {
    const val MAX_LABEL = 100
    const val MAX_OS_VERSION = 50
    const val MAX_USAGE_APPS = 50
    const val HOURS_PER_DAY = 24
    const val MAX_HOUR_MINS = 60
    const val BATCH_SIZE = 400
}

internal class FirebaseChildStatusRepository(
    private val firestore: FirebaseFirestore,
) : ChildStatusRepository {
    private fun device(target: DeviceTarget) =
        firestore
            .collection("users")
            .document(target.parentUid)
            .collection("devices")
            .document(target.deviceId)

    override suspend fun writeStatus(
        target: DeviceTarget,
        status: ChildStatus,
    ) {
        device(target).update(statusFields(status)).await()
    }

    override suspend fun writeUsage(
        target: DeviceTarget,
        summary: UsageSummary,
    ) {
        device(target)
            .collection("usage")
            .document(summary.date)
            .set(usageFields(summary))
            .await()
    }

    override suspend fun syncApps(
        target: DeviceTarget,
        apps: Map<String, String>,
    ) {
        val collection = device(target).collection("apps")
        val existing =
            collection
                .get()
                .await()
                .documents
                .associate { it.id to it.getString("label") }
        for (chunk in appSyncOps(existing, apps).chunked(StatusLimits.BATCH_SIZE)) {
            val batch = firestore.batch()
            for (op in chunk) {
                when (op) {
                    is AppSyncOp.Put -> batch.set(collection.document(op.pkg), mapOf("label" to op.label))
                    is AppSyncOp.Delete -> batch.delete(collection.document(op.pkg))
                }
            }
            batch.commit().await()
        }
    }

    private companion object {
        const val MAX_PERCENT = 100
        const val MAX_DAY_MINS = 1440
    }
}

/** The device-doc update for [status]; `osVersion` is only sent once known, so an older value is never blanked. */
internal fun statusFields(status: ChildStatus): Map<String, Any?> =
    buildMap {
        put("battery", status.battery?.coerceIn(0, MAX_PERCENT))
        put("charging", status.charging)
        put("currentApp", status.currentApp?.take(StatusLimits.MAX_LABEL)?.ifBlank { null })
        put("online", status.online)
        status.osVersion?.trim()?.take(StatusLimits.MAX_OS_VERSION)?.takeIf { it.isNotEmpty() }?.let {
            put("osVersion", it)
        }
        put("lastSeen", FieldValue.serverTimestamp())
    }

/** The `usage/{date}` doc for [summary]; `hourly` is left out unless it is exactly 24 buckets. */
internal fun usageFields(summary: UsageSummary): Map<String, Any?> =
    buildMap {
        put("totalMins", summary.totalMins.coerceIn(0, MAX_DAY_MINS))
        put(
            "apps",
            summary.apps.take(StatusLimits.MAX_USAGE_APPS).map {
                mapOf(
                    "pkg" to it.pkg,
                    "label" to it.label.take(StatusLimits.MAX_LABEL),
                    "mins" to it.mins,
                    "opens" to it.opens,
                )
            },
        )
        summary.hourly?.takeIf { it.size == StatusLimits.HOURS_PER_DAY }?.let { hourly ->
            put("hourly", hourly.map { it.coerceIn(0, StatusLimits.MAX_HOUR_MINS) })
        }
    }

private const val MAX_PERCENT = 100
private const val MAX_DAY_MINS = 1440

internal sealed interface AppSyncOp {
    data class Put(
        val pkg: String,
        val label: String,
    ) : AppSyncOp

    data class Delete(
        val pkg: String,
    ) : AppSyncOp
}

/** The writes that turn [existing] (package to label) into [desired]; unchanged apps cost nothing. */
internal fun appSyncOps(
    existing: Map<String, String?>,
    desired: Map<String, String>,
): List<AppSyncOp> {
    val wanted =
        desired.mapValues { (pkg, label) ->
            label.take(StatusLimits.MAX_LABEL).ifBlank { pkg.take(StatusLimits.MAX_LABEL) }
        }
    val puts =
        wanted
            .filter { (pkg, label) ->
                existing[pkg] != label
            }.map { (pkg, label) -> AppSyncOp.Put(pkg, label) }
    val deletes = existing.keys.filter { it !in wanted }.map { AppSyncOp.Delete(it) }
    return puts + deletes
}

/** Used when the build has no Firebase configuration: nothing is reported. */
internal object NoChildStatusRepository : ChildStatusRepository {
    override suspend fun writeStatus(
        target: DeviceTarget,
        status: ChildStatus,
    ) = Unit

    override suspend fun writeUsage(
        target: DeviceTarget,
        summary: UsageSummary,
    ) = Unit

    override suspend fun syncApps(
        target: DeviceTarget,
        apps: Map<String, String>,
    ) = Unit
}

/** In-memory repository for tests. */
class FakeChildStatusRepository : ChildStatusRepository {
    val statuses = mutableListOf<ChildStatus>()
    val usages = mutableListOf<UsageSummary>()
    val appSyncs = mutableListOf<Map<String, String>>()

    /** Set to make every write throw (simulates offline). */
    var failing = false

    private fun check() {
        if (failing) error("offline")
    }

    override suspend fun writeStatus(
        target: DeviceTarget,
        status: ChildStatus,
    ) {
        check()
        statuses += status
    }

    override suspend fun writeUsage(
        target: DeviceTarget,
        summary: UsageSummary,
    ) {
        check()
        usages += summary
    }

    override suspend fun syncApps(
        target: DeviceTarget,
        apps: Map<String, String>,
    ) {
        check()
        appSyncs += apps
    }
}
