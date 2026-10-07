package com.dculus.stayfocused.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.dculus.stayfocused.core.data.repository.BreakRepository
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.model.AppSettings
import com.dculus.stayfocused.core.model.BreakSession
import com.dculus.stayfocused.core.model.FocusSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

private object Keys {
    val onboardingComplete = booleanPreferencesKey("onboardingComplete")
    val accountSkipped = booleanPreferencesKey("accountSkipped")
    val accessibilityConsentAt = longPreferencesKey("accessibilityConsentAt")
    val aiEnabled = booleanPreferencesKey("aiEnabled")
    val breakStartedAt = longPreferencesKey("breakSession.startedAt")
    val breakEndsAt = longPreferencesKey("breakSession.endsAt")
    val breakLengthMins = intPreferencesKey("breakSession.lengthMins")
    val focusStartedAt = longPreferencesKey("focusSession.startedAt")
    val focusEndsAt = longPreferencesKey("focusSession.endsAt")
    val focusLengthMins = intPreferencesKey("focusSession.lengthMins")
}

/** A session is only present when all three of its fields are stored. */
private fun <T> Preferences.session(
    startedAt: Preferences.Key<Long>,
    endsAt: Preferences.Key<Long>,
    lengthMins: Preferences.Key<Int>,
    build: (Instant, Instant, Int) -> T,
): T? {
    val start = this[startedAt]
    val end = this[endsAt]
    val mins = this[lengthMins]
    return if (start == null || end == null || mins == null) {
        null
    } else {
        build(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), mins)
    }
}

internal class DataStoreSettingsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> =
            store.data
                .map { prefs ->
                    AppSettings(
                        onboardingComplete = prefs[Keys.onboardingComplete] ?: false,
                        accountSkipped = prefs[Keys.accountSkipped] ?: false,
                        focusSession = prefs.focusSession(),
                        accessibilityConsentAt = prefs[Keys.accessibilityConsentAt]?.let(Instant::ofEpochMilli),
                        aiEnabled = prefs[Keys.aiEnabled] ?: false,
                    )
                }.distinctUntilChanged()

        override suspend fun setOnboardingComplete(complete: Boolean) {
            store.edit { it[Keys.onboardingComplete] = complete }
        }

        override suspend fun setAccountSkipped(skipped: Boolean) {
            store.edit { it[Keys.accountSkipped] = skipped }
        }

        override suspend fun setFocusSession(session: FocusSession?) {
            store.edit { prefs ->
                if (session == null) {
                    prefs.remove(Keys.focusStartedAt)
                    prefs.remove(Keys.focusEndsAt)
                    prefs.remove(Keys.focusLengthMins)
                } else {
                    prefs[Keys.focusStartedAt] = session.startedAt.toEpochMilli()
                    prefs[Keys.focusEndsAt] = session.endsAt.toEpochMilli()
                    prefs[Keys.focusLengthMins] = session.lengthMins
                }
            }
        }

        override suspend fun setAccessibilityConsentAt(at: Instant?) {
            store.edit { prefs ->
                if (at == null) {
                    prefs.remove(Keys.accessibilityConsentAt)
                } else {
                    prefs[Keys.accessibilityConsentAt] = at.toEpochMilli()
                }
            }
        }

        override suspend fun setAiEnabled(enabled: Boolean) {
            store.edit { it[Keys.aiEnabled] = enabled }
        }

        private fun Preferences.focusSession(): FocusSession? =
            session(Keys.focusStartedAt, Keys.focusEndsAt, Keys.focusLengthMins, ::FocusSession)
    }

internal class DataStoreBreakRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
        private val clock: Clock,
    ) : BreakRepository {
        override fun observe(): Flow<BreakSession?> =
            store.data
                .map { prefs ->
                    prefs.session(Keys.breakStartedAt, Keys.breakEndsAt, Keys.breakLengthMins, ::BreakSession)
                }.distinctUntilChanged()

        override suspend fun start(mins: Int) {
            require(mins > 0) { "mins must be positive" }
            val now = clock.instant()
            store.edit { prefs ->
                prefs[Keys.breakStartedAt] = now.toEpochMilli()
                prefs[Keys.breakEndsAt] = now.plusSeconds(mins * SECONDS_PER_MINUTE).toEpochMilli()
                prefs[Keys.breakLengthMins] = mins
            }
        }

        override suspend fun end() {
            store.edit { prefs ->
                prefs.remove(Keys.breakStartedAt)
                prefs.remove(Keys.breakEndsAt)
                prefs.remove(Keys.breakLengthMins)
            }
        }

        private companion object {
            const val SECONDS_PER_MINUTE = 60L
        }
    }
