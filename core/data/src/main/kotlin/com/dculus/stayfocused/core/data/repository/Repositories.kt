package com.dculus.stayfocused.core.data.repository

import com.dculus.stayfocused.core.model.AppSettings
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BreakSession
import com.dculus.stayfocused.core.model.FocusSession
import com.dculus.stayfocused.core.model.LinkedDevice
import com.dculus.stayfocused.core.model.LockedApp
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface BlockRepository {
    /** Blocks of [target], oldest first (by `createdAt`). */
    fun observeByTarget(target: BlockTarget): Flow<List<Block>>

    fun observe(id: String): Flow<Block?>

    suspend fun get(id: String): Block?

    /** Inserts or replaces the block, including its app set. */
    suspend fun upsert(block: Block)

    /** No-op when [id] does not exist. */
    suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    )

    suspend fun delete(id: String)
}

interface LockedAppsRepository {
    /** Locked apps of every target, oldest first (by `since`). */
    fun observeAll(): Flow<List<LockedApp>>

    fun observeByTarget(target: BlockTarget): Flow<List<LockedApp>>

    /** Locks [pkg] for [target] as of now; locking an already locked app keeps the original `since`. */
    suspend fun lock(
        pkg: String,
        target: BlockTarget,
    )

    suspend fun unlock(
        pkg: String,
        target: BlockTarget,
    )
}

interface BreakRepository {
    /**
     * The stored break, or null. An elapsed break stays stored until [end] is called;
     * callers compare `endsAt` with the clock.
     */
    fun observe(): Flow<BreakSession?>

    /** Starts a break of [mins] minutes from now, replacing any current one. [mins] must be positive. */
    suspend fun start(mins: Int)

    suspend fun end()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setOnboardingComplete(complete: Boolean)

    suspend fun setAccountSkipped(skipped: Boolean)

    suspend fun setFocusSession(session: FocusSession?)

    suspend fun setAccessibilityConsentAt(at: Instant?)

    suspend fun setAiEnabled(enabled: Boolean)
}

interface LinkedDevicesRepository {
    /** Child phones linked to this one, oldest link first. Empty until device linking ships (M7). */
    fun observeAll(): Flow<List<LinkedDevice>>
}
