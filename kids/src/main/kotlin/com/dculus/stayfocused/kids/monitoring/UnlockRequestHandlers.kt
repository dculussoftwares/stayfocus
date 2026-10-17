package com.dculus.stayfocused.kids.monitoring

import android.content.Context
import com.dculus.stayfocused.core.blocking.engine.TemporaryAllowances
import com.dculus.stayfocused.core.sync.CommandHandler
import com.dculus.stayfocused.core.sync.CommandType
import com.dculus.stayfocused.core.sync.RemoteCommand
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which app each request of this phone was for. A request document carries only the app's label (the parent reads it),
 * so the package that an approval has to unlock is remembered here, by request id, until the parent answers.
 */
interface PendingRequestStore {
    fun remember(
        reqId: String,
        pkg: String,
    )

    /** The package of request [reqId], or null when it is unknown (answered already, or not made on this phone). */
    fun packageOf(reqId: String): String?

    fun forget(reqId: String)
}

@Singleton
internal class SharedPreferencesPendingRequestStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : PendingRequestStore {
        private val prefs = context.getSharedPreferences("kids_requests", Context.MODE_PRIVATE)

        @Synchronized
        override fun remember(
            reqId: String,
            pkg: String,
        ) = save((load().filter { it.first != reqId } + (reqId to pkg)).takeLast(MAX_KEPT))

        @Synchronized
        override fun packageOf(reqId: String): String? = load().firstOrNull { it.first == reqId }?.second

        @Synchronized
        override fun forget(reqId: String) = save(load().filter { it.first != reqId })

        private fun save(entries: List<Pair<String, String>>) {
            val json = JSONArray()
            entries.forEach { json.put(JSONArray().put(it.first).put(it.second)) }
            prefs.edit().putString(KEY, json.toString()).apply()
        }

        private fun load(): List<Pair<String, String>> =
            try {
                val json = JSONArray(prefs.getString(KEY, null) ?: "[]")
                List(json.length()) { i -> json.getJSONArray(i).let { it.getString(0) to it.getString(1) } }
            } catch (e: JSONException) {
                Timber.w(e, "Pending requests unreadable; starting over")
                emptyList()
            }

        private companion object {
            const val KEY = "pending"

            /** A child makes a handful of requests a day, and a parent answers within hours. */
            const val MAX_KEPT = 100
        }
    }

/** In-memory store for tests. */
class FakePendingRequestStore : PendingRequestStore {
    private val map = linkedMapOf<String, String>()

    override fun remember(
        reqId: String,
        pkg: String,
    ) {
        map[reqId] = pkg
    }

    override fun packageOf(reqId: String) = map[reqId]

    override fun forget(reqId: String) {
        map.remove(reqId)
    }
}

/**
 * `APPROVE_REQUEST`: the app is open for the approved minutes ([TemporaryAllowances], read by the blocking engine, so
 * the block screen goes away within a second and the rule applies again when the time is up). A request this phone
 * does not know ends as failed instead of unlocking something nobody asked for.
 */
class ApproveRequestHandler
    @Inject
    internal constructor(
        private val requests: PendingRequestStore,
        private val allowances: TemporaryAllowances,
        private val mirror: ParentRulesMirror,
        private val clock: Clock,
    ) : CommandHandler {
        override val type = CommandType.APPROVE_REQUEST

        override suspend fun handle(command: RemoteCommand) =
            mirror.whileLinked {
                val approve = command as RemoteCommand.ApproveRequest
                val pkg = checkNotNull(requests.packageOf(approve.reqId)) { "Unknown request" }
                allowances.grant(pkg, clock.instant().plus(Duration.ofMinutes(approve.mins.toLong())))
                requests.forget(approve.reqId)
            }
    }

/** `DENY_REQUEST`: nothing opens; the request is forgotten. */
class DenyRequestHandler
    @Inject
    internal constructor(
        private val requests: PendingRequestStore,
        private val mirror: ParentRulesMirror,
    ) : CommandHandler {
        override val type = CommandType.DENY_REQUEST

        override suspend fun handle(command: RemoteCommand) =
            mirror.whileLinked { requests.forget((command as RemoteCommand.DenyRequest).reqId) }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UnlockRequestModule {
    @Binds
    abstract fun store(impl: SharedPreferencesPendingRequestStore): PendingRequestStore

    @Binds
    @IntoSet
    abstract fun approve(impl: ApproveRequestHandler): CommandHandler

    @Binds
    @IntoSet
    abstract fun deny(impl: DenyRequestHandler): CommandHandler
}
