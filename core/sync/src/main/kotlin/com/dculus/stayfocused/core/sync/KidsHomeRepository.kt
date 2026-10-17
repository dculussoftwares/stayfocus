package com.dculus.stayfocused.core.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** What the server says about this phone's device document. */
enum class DeviceLinkState {
    Linked,

    /** The server confirmed the document is gone: the parent unlinked this phone or deleted their account. */
    Gone,

    /**
     * The server refused the read. That is what a missing document looks like to the rules, but a rejected App Check
     * token looks the same, so callers ask again before acting on it. The flow ends after this.
     */
    Unreadable,
}

/** One rule the parent set for this phone, in the words the Kids home needs. Fields depend on [type]. */
data class KidsRule(
    val id: String,
    val name: String,
    val type: KidsRuleType,
    /** LIMIT: minutes allowed per day, or per hour when [hourly]. */
    val limitMins: Int? = null,
    val hourly: Boolean = false,
    /** CYCLE: minutes of use and of rest. */
    val useMins: Int? = null,
    val restMins: Int? = null,
    /** SCHEDULE: `HH:mm` bounds and the days they apply (bit 0 = Monday ... bit 6 = Sunday). */
    val start: String? = null,
    val end: String? = null,
    val days: Int = ALL_DAYS,
    /** NOW: minutes. */
    val durationMins: Int? = null,
) {
    companion object {
        const val ALL_DAYS = 0b1111111
    }
}

enum class KidsRuleType { Limit, Cycle, Schedule, Now }

/** What the Kids home reads from the parent's tree. Read-only: the child never writes rules. */
interface KidsHomeRepository {
    /**
     * Emits [DeviceLinkState.Gone] once the server confirms the device document is gone, and
     * [DeviceLinkState.Unreadable] (then ends) when the server refuses the read. A stale cached copy never counts as
     * gone. The flow fails on other errors; callers retry after a failure.
     */
    fun observeLink(target: DeviceTarget): Flow<DeviceLinkState>

    /** The enabled rules for this phone, by name. The flow fails when the listener is denied or lost. */
    fun observeRules(target: DeviceTarget): Flow<List<KidsRule>>
}

internal class FirebaseKidsHomeRepository(
    private val firestore: FirebaseFirestore,
) : KidsHomeRepository {
    private fun device(target: DeviceTarget) =
        firestore
            .collection("users")
            .document(target.parentUid)
            .collection("devices")
            .document(target.deviceId)

    override fun observeLink(target: DeviceTarget): Flow<DeviceLinkState> =
        callbackFlow {
            val registration =
                device(target).addSnapshotListener { snapshot, error ->
                    when {
                        error != null && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                            trySend(DeviceLinkState.Unreadable)
                            close()
                        }

                        error != null -> {
                            close(error)
                        }

                        snapshot != null -> {
                            snapshot.toLinkState()?.let { trySend(it) }
                        }
                    }
                }
            awaitClose { registration.remove() }
        }

    override fun observeRules(target: DeviceTarget): Flow<List<KidsRule>> =
        callbackFlow {
            val registration =
                device(target).collection("blocks").addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                    } else if (snapshot != null) {
                        val rules = snapshot.documents.mapNotNull { parseRule(it.id, it.data.orEmpty()) }
                        trySend(rules.sortedBy { it.name })
                    }
                }
            awaitClose { registration.remove() }
        }
}

/** A missing document counts only when it comes from the server; the cache says nothing about a document never seen. */
private fun DocumentSnapshot.toLinkState(): DeviceLinkState? =
    when {
        exists() -> DeviceLinkState.Linked
        metadata.isFromCache -> null
        else -> DeviceLinkState.Gone
    }

/**
 * A block document as a rule, or null when it is switched off, unreadable or lacks the fields its type needs (the
 * rules bound the fields but cannot check list contents, so this reads defensively).
 */
internal fun parseRule(
    id: String,
    data: Map<String, Any?>,
): KidsRule? {
    val name = (data["name"] as? String)?.takeIf { it.isNotBlank() }
    val type = ruleType(data["type"])
    if (data["enabled"] != true || name == null || type == null) return null
    val range = data["range"] as? Map<*, *>
    val rule =
        KidsRule(
            id = id,
            name = name,
            type = type,
            limitMins = (data["limitMins"] as? Number)?.toInt(),
            hourly = data["period"] == "HOURLY",
            useMins = (data["useMins"] as? Number)?.toInt(),
            restMins = (data["restMins"] as? Number)?.toInt(),
            start = range?.get("start") as? String,
            end = range?.get("end") as? String,
            days = (data["days"] as? Number)?.toInt()?.takeIf { it in 0..KidsRule.ALL_DAYS } ?: KidsRule.ALL_DAYS,
            durationMins = (data["durationMins"] as? Number)?.toInt(),
        )
    return rule.takeIf { it.hasItsFields() }
}

private fun KidsRule.hasItsFields(): Boolean =
    when (type) {
        KidsRuleType.Limit -> limitMins != null
        KidsRuleType.Cycle -> useMins != null && restMins != null
        KidsRuleType.Schedule -> start != null && end != null
        KidsRuleType.Now -> durationMins != null
    }

private fun ruleType(value: Any?): KidsRuleType? =
    when (value) {
        "LIMIT" -> KidsRuleType.Limit
        "CYCLE" -> KidsRuleType.Cycle
        "SCHEDULE" -> KidsRuleType.Schedule
        "NOW" -> KidsRuleType.Now
        else -> null
    }

/** Used when the build has no Firebase configuration: nothing is linked, so nothing is observed. */
internal object NoKidsHomeRepository : KidsHomeRepository {
    override fun observeLink(target: DeviceTarget): Flow<DeviceLinkState> = emptyFlow()

    override fun observeRules(target: DeviceTarget): Flow<List<KidsRule>> = flowOf(emptyList())
}

/**
 * In-memory repository for tests: the test pushes link states into [link] and the rules into [rules]. Each queued
 * entry of [linkRuns] is served to one `observeLink` call as a flow that ends (like a refused read); when none is
 * left, [link] is used.
 */
class FakeKidsHomeRepository : KidsHomeRepository {
    val link = MutableSharedFlow<DeviceLinkState>(replay = 1)
    val linkRuns = ArrayDeque<List<DeviceLinkState>>()
    val rules = MutableStateFlow<List<KidsRule>>(emptyList())

    /** How many times the link was watched. */
    var linkWatches = 0

    override fun observeLink(target: DeviceTarget): Flow<DeviceLinkState> {
        linkWatches++
        return linkRuns.removeFirstOrNull()?.let { flowOf(*it.toTypedArray()) } ?: link
    }

    override fun observeRules(target: DeviceTarget): Flow<List<KidsRule>> = rules
}
