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
 * Remembers how each handled command ended across process restarts, so a redelivered command (its acknowledgement was
 * lost offline) is acknowledged again instead of run again. Entries are never dropped by time, because the phone's
 * clock can be wrong or corrected; only the oldest go once [MAX_KEPT] is exceeded, far more than a parent sends in
 * the 24 h a command can still run.
 */
@Singleton
internal class SharedPreferencesProcessedCommands
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : ProcessedCommands {
        private val prefs = context.getSharedPreferences("kids_commands", Context.MODE_PRIVATE)

        @Synchronized
        override fun statusOf(id: String): CommandStatus? = load().firstOrNull { it.first == id }?.second

        @Synchronized
        override fun record(
            id: String,
            status: CommandStatus,
        ) {
            val kept = (load().filter { it.first != id } + (id to status)).takeLast(MAX_KEPT)
            val json = JSONArray()
            kept.forEach { json.put(JSONArray().put(it.first).put(it.second.wire)) }
            prefs.edit().putString(KEY, json.toString()).apply()
        }

        private fun load(): List<Pair<String, CommandStatus>> =
            try {
                val json = JSONArray(prefs.getString(KEY, null) ?: "[]")
                List(json.length()) { i ->
                    val row = json.getJSONArray(i)
                    row.getString(0) to CommandStatus.fromWire(row.getString(1))
                }
            } catch (e: JSONException) {
                Timber.w(e, "Processed-command history unreadable; starting over")
                emptyList()
            }

        private companion object {
            const val KEY = "processed"
            const val MAX_KEPT = 2000
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CommandsModule {
    @Binds
    abstract fun processed(impl: SharedPreferencesProcessedCommands): ProcessedCommands
}
