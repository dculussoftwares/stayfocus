package com.dculus.stayfocused.feature.home

import android.content.Intent
import com.dculus.stayfocused.core.model.AppInfo
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockSource
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BlockType
import com.dculus.stayfocused.core.model.DaysOfWeek
import com.dculus.stayfocused.core.model.KnownApps
import com.dculus.stayfocused.core.model.LimitPeriod
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.TamperAlert
import com.dculus.stayfocused.core.model.TamperKind
import com.dculus.stayfocused.core.model.TimeRange
import com.dculus.stayfocused.core.usage.AppUsageStat
import com.dculus.stayfocused.core.usage.DayUsageStats
import com.dculus.stayfocused.core.usage.InstalledAppsRepository
import com.dculus.stayfocused.core.usage.UsageAccess
import com.dculus.stayfocused.core.usage.UsageAverages
import com.dculus.stayfocused.core.usage.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import java.time.LocalDate

internal fun pkg(id: String): String = KnownApps.packages.getValue(id)

private val Epoch: Instant = Instant.parse("2026-01-01T00:00:00Z")
internal val TestDay: LocalDate = LocalDate.of(2026, 10, 6)

/** The prototype's hourly minutes for today (index = hour). */
internal val PrototypeHours = listOf(0, 0, 0, 0, 0, 0, 2, 6, 14, 9, 5, 8, 12, 4, 3, 7, 10, 6, 9, 18, 22, 15, 8, 2)

/** 147 minutes in total, 63 opens, 48 unlocks: the prototype's "typical day". */
internal fun typicalDay(): DayUsageStats =
    DayUsageStats(
        date = TestDay,
        totalMillis = 147 * 60_000L,
        apps =
            listOf(
                AppUsageStat(pkg("instagram"), 41 * 60_000L, opens = 12, firstAfterUnlock = 4),
                AppUsageStat(pkg("youtube"), 38 * 60_000L, opens = 6, firstAfterUnlock = 2),
                AppUsageStat(pkg("whatsapp"), 68 * 60_000L, opens = 45, firstAfterUnlock = 9),
            ),
        hourlyMillis = PrototypeHours.map { it * 60_000L },
        unlocks = 48,
        hourlyUnlocks = List(24) { 2 },
    )

internal fun averages(totalMins: Int) =
    UsageAverages(daysCounted = 7, avgTotalMillis = totalMins * 60_000L, avgOpens = 58, avgUnlocks = 51)

internal class FakeUsage(
    initial: DayUsageStats = DayUsageStats.empty(TestDay),
    avg: UsageAverages = UsageAverages.NONE,
    blocked: Int = 0,
) : UsageRepository {
    val today = MutableStateFlow(initial)
    val averages = MutableStateFlow(avg)
    val blocked = MutableStateFlow(blocked)
    var refreshes = 0
    var backfills = 0

    override fun today(): Flow<DayUsageStats> = today

    override fun requestRefresh() {
        refreshes++
    }

    override fun day(date: LocalDate): Flow<DayUsageStats?> = emptyFlow()

    override fun averages(): Flow<UsageAverages> = averages

    override fun blockedToday(): Flow<Int> = blocked

    override suspend fun backfill() {
        backfills++
    }
}

internal class FakeUsageAccess(
    var granted: Boolean = true,
) : UsageAccess {
    override fun isGranted(): Boolean = granted

    val intent = Intent()

    override fun settingsIntent(): Intent = intent
}

internal class FakeInstalledApps(
    apps: List<AppInfo> = emptyList(),
) : InstalledAppsRepository {
    val apps = MutableStateFlow(apps)

    override fun observeLaunchableApps(): Flow<List<AppInfo>> = apps
}

internal fun cycleBlock(
    id: String = "b1",
    enabled: Boolean = true,
    target: BlockTarget = BlockTarget.ThisPhone,
) = Block(
    id = id,
    target = target,
    type = BlockType.CYCLE,
    name = "Mindful scrolling",
    apps = setOf(pkg("instagram"), pkg("youtube")),
    limitMins = null,
    period = null,
    useMins = 10,
    restMins = 30,
    range = null,
    durationMins = null,
    startedAt = null,
    days = DaysOfWeek.ALL,
    enabled = enabled,
    createdAt = Epoch,
    source = BlockSource.TEMPLATE,
)

internal fun limitBlock(
    id: String = "b2",
    enabled: Boolean = true,
) = Block(
    id = id,
    target = BlockTarget.ThisPhone,
    type = BlockType.LIMIT,
    name = "Social limit",
    apps = setOf(pkg("reddit"), pkg("x")),
    limitMins = 30,
    period = LimitPeriod.DAILY,
    useMins = null,
    restMins = null,
    range = null,
    durationMins = null,
    startedAt = null,
    days = DaysOfWeek.ALL,
    enabled = enabled,
    createdAt = Epoch.plusSeconds(1),
    source = BlockSource.MANUAL,
)

internal fun scheduleBlock(
    id: String = "b3",
    enabled: Boolean = false,
) = Block(
    id = id,
    target = BlockTarget.ThisPhone,
    type = BlockType.SCHEDULE,
    name = "Work hours",
    apps = setOf(pkg("instagram"), pkg("youtube"), pkg("reddit")),
    limitMins = null,
    period = null,
    useMins = null,
    restMins = null,
    range = TimeRange.parse("09:00–17:00"),
    durationMins = null,
    startedAt = null,
    days = DaysOfWeek.WEEKDAYS,
    enabled = enabled,
    createdAt = Epoch.plusSeconds(2),
    source = BlockSource.MANUAL,
)

internal fun device(
    id: String = "d1",
    name: String = "Aarav's phone",
    alerts: Int = 0,
) = LinkedDevice(
    id = id,
    name = name,
    model = "Pixel 6a",
    battery = 80,
    currentApp = "Instagram",
    online = true,
    lastSeen = Epoch,
    alerts =
        List(
            alerts,
        ) { TamperAlert("a$it", TamperKind.PERMISSION_LOST, "accessibility", Epoch, dismissed = false) },
    requests = emptyList(),
    focusEndsAt = null,
)
