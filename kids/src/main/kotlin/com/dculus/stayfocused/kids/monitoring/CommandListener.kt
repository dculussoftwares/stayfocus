package com.dculus.stayfocused.kids.monitoring

import android.content.Context
import com.dculus.stayfocused.core.sync.CommandProcessor
import com.dculus.stayfocused.core.sync.CommandRepository
import com.dculus.stayfocused.core.sync.CommandStatus
import com.dculus.stayfocused.core.sync.DeviceAuth
import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.ProcessedCommands
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

internal object CommandCadence {
    const val RETRY_MILLIS = 30_000L
}

/**
 * Listens for the parent's pending commands while the monitoring service runs and hands each one to the
 * [CommandProcessor]. Firestore keeps the listener across short network loss; when it is denied or dies anyway, it is
 * attached again after a pause. The service starts it on every (re)start and on boot, so the listener always follows
 * the service.
 */
class CommandListener
    @Inject
    internal constructor(
        private val repository: CommandRepository,
        private val deviceAuth: DeviceAuth,
        private val processor: CommandProcessor,
    ) {
        /** Runs until cancelled. */
        @Suppress("TooGenericExceptionCaught")
        suspend fun run(target: DeviceTarget) {
            while (deviceAuth.ensureSignedIn() == null) {
                Timber.w("Not listening for commands yet: no anonymous sign-in")
                delay(CommandCadence.RETRY_MILLIS)
            }
            while (true) {
                try {
                    repository.observePending(target).collect { commands ->
                        // Oldest first; a command without a server time yet goes last.
                        for (command in commands.sortedBy { it.createdAtMillis ?: Long.MAX_VALUE }) {
                            processor.process(target, command)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Command listener lost; attaching again")
                }
                delay(CommandCadence.RETRY_MILLIS)
            }
        }
    }

/**
 * Remembers how each handled command ended across process restarts. An entry is kept for twice the 24 h command
 * lifetime and only then dropped: past that a redelivered command is expired before it could run, so forgetting it is
 * safe, whereas a count limit could forget a command whose acknowledgement never reached the server.
 */
@Singleton
internal class SharedPreferencesProcessedCommands
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val clock: Clock,
    ) : ProcessedCommands {
        private val prefs = context.getSharedPreferences("kids_commands", Context.MODE_PRIVATE)

        @Synchronized
        override fun statusOf(id: String): CommandStatus? = load().firstOrNull { it.id == id }?.status

        @Synchronized
        override fun record(
            id: String,
            status: CommandStatus,
        ) {
            val now = clock.millis()
            val kept =
                load().filter { it.id != id && now - it.atMillis <= KEEP_MILLIS } + Entry(id, status, now)
            val json = JSONArray()
            kept.forEach { json.put(JSONArray().put(it.id).put(it.status.wire).put(it.atMillis)) }
            prefs.edit().putString(KEY, json.toString()).apply()
        }

        private fun load(): List<Entry> =
            try {
                val json = JSONArray(prefs.getString(KEY, null) ?: "[]")
                List(json.length()) { i ->
                    val row = json.getJSONArray(i)
                    Entry(row.getString(0), CommandStatus.fromWire(row.getString(1)), row.getLong(2))
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") e: JSONException,
            ) {
                Timber.w(e, "Processed-command history unreadable; starting over")
                emptyList()
            }

        private data class Entry(
            val id: String,
            val status: CommandStatus,
            val atMillis: Long,
        )

        private companion object {
            const val KEY = "processed"
            val KEEP_MILLIS = 2 * CommandProcessor.MAX_AGE_MILLIS
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CommandsModule {
    @Binds
    abstract fun processed(impl: SharedPreferencesProcessedCommands): ProcessedCommands
}
