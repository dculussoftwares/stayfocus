package com.dculus.stayfocused.core.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** An activity that came to the front: [pkg]/[className] at [timeMillis] (wall clock). */
data class ResumeEvent(
    val pkg: String,
    val className: String,
    val timeMillis: Long,
)

/** Reads which activities came to the front; the foreground source of the standard blocking engine. */
interface ResumeEventReader {
    /**
     * `ACTIVITY_RESUMED` (API 29+) / `MOVE_TO_FOREGROUND` (API 26-28) events in [[fromMillis], [toMillis]), oldest
     * first.
     *
     * @throws SecurityException when Usage access is not granted (any more).
     */
    fun resumedSince(
        fromMillis: Long,
        toMillis: Long,
    ): List<ResumeEvent>
}

@Singleton
class AndroidResumeEventReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val usageAccess: UsageAccess,
    ) : ResumeEventReader {
        override fun resumedSince(
            fromMillis: Long,
            toMillis: Long,
        ): List<ResumeEvent> {
            if (!usageAccess.isGranted()) throw SecurityException("Usage access is not granted")
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            // queryEvents returns null when it cannot answer; the access check above covers the revoked case.
            val events = manager.queryEvents(fromMillis, toMillis) ?: return emptyList()
            val result = mutableListOf<ResumeEvent>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (mapEventType(event.eventType, Build.VERSION.SDK_INT) != RawEventType.ACTIVITY_RESUMED) continue
                result += ResumeEvent(event.packageName.orEmpty(), event.className.orEmpty(), event.timeStamp)
            }
            return result
        }
    }
