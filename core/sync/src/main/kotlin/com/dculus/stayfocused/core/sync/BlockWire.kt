package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.CycleScope
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.TimeRange
import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.Date

private const val MAX_DAYS_MASK = 0x7F

/** Limits of `firestore.rules` for a block document. */
internal const val MAX_NAME = 100
internal const val MAX_APPS = 300

/**
 * The Firestore form of a [Block] (`devices/{id}/blocks/{blockId}`); the keys are exactly the ones `firestore.rules`
 * allows. Optional fields a block type does not use are left out.
 */
internal fun Block.toWire(): Map<String, Any> =
    buildMap {
        put("id", id)
        put("type", type.name)
        put("name", name.take(MAX_NAME))
        put("apps", apps.sorted())
        limitMins?.let { put("limitMins", it) }
        period?.let { put("period", it.name) }
        useMins?.let { put("useMins", it) }
        restMins?.let { put("restMins", it) }
        // Only a shared window is sent, so a block that counts per app stays valid against older rules.
        if (type == BlockType.CYCLE && cycleScope == CycleScope.TOTAL) put("cycleScope", cycleScope.name)
        range?.let { put("range", mapOf("start" to it.start.toString(), "end" to it.end.toString())) }
        durationMins?.let { put("durationMins", it) }
        startedAt?.let { put("startedAt", it.toTimestamp()) }
        put("days", days.mask)
        put("enabled", enabled)
        put("createdAt", createdAt.toTimestamp())
        put("source", source.name)
    }

/**
 * Reads a block back as the Kids phone enforces it (target = this phone). Null for a document that does not fit its
 * type, so one bad document never takes the others down.
 */
@Suppress("ReturnCount") // one early exit per required field keeps the parsing flat
internal fun parseWireBlock(
    id: String,
    data: Map<String, Any?>,
): Block? {
    val type = enumOrNull<BlockType>(data["type"]) ?: return null
    val mask = data.int("days")?.takeIf { it in 0..MAX_DAYS_MASK } ?: return null
    val enabled = data["enabled"] as? Boolean ?: return null
    val block =
        Block(
            id = id,
            target = BlockTarget.ThisPhone,
            type = type,
            name = (data["name"] as? String).orEmpty(),
            apps = (data["apps"] as? List<*>).orEmpty().filterIsInstance<String>().toSet(),
            limitMins = data.int("limitMins"),
            period = enumOrNull<LimitPeriod>(data["period"]),
            useMins = data.int("useMins"),
            restMins = data.int("restMins"),
            range = parseRange(data["range"]),
            durationMins = data.int("durationMins"),
            startedAt = data.instant("startedAt"),
            days = DaysOfWeek(mask),
            enabled = enabled,
            createdAt = data.instant("createdAt") ?: Instant.EPOCH,
            source = enumOrNull<BlockSource>(data["source"]) ?: BlockSource.MANUAL,
            cycleScope = enumOrNull<CycleScope>(data["cycleScope"]) ?: CycleScope.PER_APP,
        )
    return block.takeIf { it.hasFieldsOfItsType() }
}

private fun Block.hasFieldsOfItsType(): Boolean =
    when (type) {
        BlockType.LIMIT -> limitMins != null && period != null
        BlockType.CYCLE -> useMins != null && restMins != null
        BlockType.SCHEDULE -> range != null
        BlockType.NOW -> durationMins != null && startedAt != null
    }

@Suppress("ReturnCount")
private fun parseRange(value: Any?): TimeRange? {
    val map = value as? Map<*, *> ?: return null
    val start = map["start"] as? String ?: return null
    val end = map["end"] as? String ?: return null
    return try {
        TimeRange(LocalTime.parse(start), LocalTime.parse(end))
    } catch (_: DateTimeParseException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private inline fun <reified E : Enum<E>> enumOrNull(value: Any?): E? = enumValues<E>().firstOrNull { it.name == value }

private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

private fun Map<String, Any?>.instant(key: String): Instant? = (this[key] as? Timestamp)?.toDate()?.toInstant()

private fun Instant.toTimestamp() = Timestamp(Date.from(this))
