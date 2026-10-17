package com.dculus.stayfocused.core.blocking.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dculus.stayfocused.core.model.TemporaryAllowance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Active "unlock for N minutes" grants that can also be given (the Kids app does when a parent approves). */
interface TemporaryAllowances : TemporaryAllowanceSource {
    /**
     * Lets [pkg] through until [until]. A later end replaces an earlier one for the same app; an earlier or past one
     * changes nothing (a parent's second, shorter approval never cuts the first short).
     */
    fun grant(
        pkg: String,
        until: Instant,
    )
}

/**
 * Allowances kept in preferences, so an approved grant survives the process being killed. Entries that ended are
 * dropped whenever the list is read or written. Plain preferences (not DataStore): grants are tiny and the write must
 * be on disk before the command is acknowledged.
 */
@Singleton
class SharedPreferencesTemporaryAllowances internal constructor(
    private val prefs: SharedPreferences,
    private val clock: Clock,
) : TemporaryAllowances {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        clock: Clock,
    ) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE), clock)

    private val state = MutableStateFlow(load())

    override fun observe(): Flow<List<TemporaryAllowance>> = state

    @Synchronized
    override fun grant(
        pkg: String,
        until: Instant,
    ) {
        val now = clock.instant()
        val current = load().filter { it.pkg != pkg || it.until > until }
        // A longer grant for the same app is already there: keep it.
        val next =
            if (until > now &&
                current.none { it.pkg == pkg }
            ) {
                current + TemporaryAllowance(pkg, until)
            } else {
                current
            }
        save(next)
        state.value = next
    }

    private fun load(): List<TemporaryAllowance> {
        val now = clock.instant()
        return try {
            val json = JSONArray(prefs.getString(KEY, null) ?: "[]")
            List(json.length()) { i ->
                val row = json.getJSONArray(i)
                TemporaryAllowance(row.getString(0), Instant.ofEpochMilli(row.getLong(1)))
            }.filter { it.until > now }
        } catch (e: JSONException) {
            Log.w(TAG, "Temporary allowances unreadable; starting over", e)
            emptyList()
        }
    }

    private fun save(allowances: List<TemporaryAllowance>) {
        val json = JSONArray()
        allowances.forEach { json.put(JSONArray().put(it.pkg).put(it.until.toEpochMilli())) }
        prefs.edit().putString(KEY, json.toString()).commit()
    }

    private companion object {
        const val TAG = "TemporaryAllowances"
        const val FILE = "temporary_allowances"
        const val KEY = "allowances"
    }
}
