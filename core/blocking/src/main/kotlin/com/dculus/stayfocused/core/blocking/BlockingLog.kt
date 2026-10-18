package com.dculus.stayfocused.core.blocking

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * TEMPORARY diagnostics for "the blocked app sometimes opens anyway". Appends one line per event to
 * `<external files dir>/logs/blocking.log` (on a phone: `Android/data/<applicationId>/files/logs/blocking.log`),
 * falling back to the app's private files dir. Does nothing until [init] is called, so JVM tests are unaffected.
 * Remove together with the share button once the bug is understood.
 */
object BlockingLog {
    private const val MAX_BYTES = 1_000_000L
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "blocking-log").apply { isDaemon = true } }

    @Volatile private var file: File? = null

    /** The log file, or null before [init]. */
    fun file(): File? = file

    /** The newest [lines] lines (small enough to travel in a share intent), or a note when there is no log. */
    fun tail(lines: Int = 600): String {
        val f = file ?: return "No log yet: turn the accessibility service on first."
        return try {
            f
                .readLines()
                .takeLast(lines)
                .joinToString("\n")
                .ifEmpty { "Log is empty." }
        } catch (e: java.io.IOException) {
            "Could not read ${f.path}: ${e.message}"
        }
    }

    fun init(context: Context) {
        if (file != null) return
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs").apply { mkdirs() }
        file = File(dir, "blocking.log")
        log("Log", "---- service connected ----")
    }

    fun log(
        tag: String,
        message: String,
    ) {
        val target = file ?: return
        val line = "${stamp()} $tag: $message\n"
        Log.d("StayFocused", "$tag: $message")
        writer.execute {
            try {
                if (target.length() > MAX_BYTES) target.writeText("") // keep the newest ~1 MB, simply
                target.appendText(line)
            } catch (_: java.io.IOException) {
                // Diagnostics must never break blocking.
            }
        }
    }

    private fun stamp(): String = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
}
