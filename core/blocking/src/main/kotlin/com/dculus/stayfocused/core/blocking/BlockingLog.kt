package com.dculus.stayfocused.core.blocking

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * TEMPORARY diagnostics for "the blocked app sometimes opens anyway". Appends one line per event to
 * `<external files dir>/logs/blocking.log` (on a phone: `Android/data/<applicationId>/files/logs/blocking.log`),
 * falling back to the app's private files dir.
 *
 * It records which apps were opened, so it only runs in debuggable builds ([isEnabled]); a release build never
 * writes it and deletes a leftover file. [init] runs at app start (and again from the accessibility service);
 * nothing is written before it, so JVM tests are unaffected. All file
 * access happens on a background thread (or the caller's, for [tail]). Remove together with the share button once
 * the bug is understood.
 */
object BlockingLog {
    private const val MAX_BYTES = 1_000_000L
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "blocking-log").apply { isDaemon = true } }

    @Volatile private var appContext: Context? = null
    private var logFile: File? = null

    /** Whether diagnostics are collected: only in a debuggable build. */
    fun isEnabled(context: Context): Boolean = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app.takeIf(::isEnabled)
        if (appContext == null) {
            writer.execute { runCatching { resolve(app).delete() } } // a leftover from an earlier debug build
            return
        }
        log("Log", "---- log started ----")
    }

    /** The newest [lines] lines (small enough to travel in a share intent); null when there is nothing to share. */
    fun tail(lines: Int = 600): String? {
        val app = appContext ?: return null
        return try {
            resolve(app)
                .readLines()
                .takeLast(lines)
                .joinToString("\n")
                .ifEmpty { null }
        } catch (_: java.io.IOException) {
            null
        }
    }

    fun log(
        tag: String,
        message: String,
    ) {
        val app = appContext ?: return
        val line = "${stamp()} $tag: $message\n"
        Log.d("StayFocused", "$tag: $message")
        writer.execute {
            try {
                val target = resolve(app)
                if (target.length() > MAX_BYTES) target.writeText("") // keep the newest ~1 MB, simply
                target.appendText(line)
            } catch (_: java.io.IOException) {
                // Diagnostics must never break blocking.
            }
        }
    }

    @Synchronized
    private fun resolve(app: Context): File =
        logFile ?: File(File(app.getExternalFilesDir(null) ?: app.filesDir, "logs").apply { mkdirs() }, "blocking.log")
            .also { logFile = it }

    private fun stamp(): String = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
