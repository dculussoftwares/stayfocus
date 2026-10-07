package com.dculus.stayfocused.core.testing

import com.dculus.stayfocused.core.data.repository.BlockRepository
import com.dculus.stayfocused.core.data.repository.BreakRepository
import com.dculus.stayfocused.core.data.repository.LockedAppsRepository
import com.dculus.stayfocused.core.data.repository.SettingsRepository
import com.dculus.stayfocused.core.model.AppSettings
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.BreakSession
import com.dculus.stayfocused.core.model.FocusSession
import com.dculus.stayfocused.core.model.LockedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant

/** In-memory [BlockRepository]; behaviour is pinned to the Room implementation by a shared contract test. */
class FakeBlockRepository : BlockRepository {
    private val blocks = MutableStateFlow<Map<String, Block>>(emptyMap())

    override fun observeByTarget(target: BlockTarget): Flow<List<Block>> =
        blocks
            .map { all -> all.values.filter { it.target == target }.sortedBy { it.createdAt } }
            .distinctUntilChanged()

    override fun observe(id: String): Flow<Block?> = blocks.map { it[id] }.distinctUntilChanged()

    override suspend fun get(id: String): Block? = blocks.value[id]

    override suspend fun upsert(block: Block) = blocks.update { it + (block.id to block) }

    override suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ) = blocks.update { all -> all[id]?.let { all + (id to it.copy(enabled = enabled)) } ?: all }

    override suspend fun delete(id: String) = blocks.update { it - id }
}

class FakeLockedAppsRepository(
    private val clock: Clock = TestClock(),
) : LockedAppsRepository {
    private val apps = MutableStateFlow<List<LockedApp>>(emptyList())

    override fun observeAll(): Flow<List<LockedApp>> =
        apps.map { all -> all.sortedBy { it.since } }.distinctUntilChanged()

    override fun observeByTarget(target: BlockTarget): Flow<List<LockedApp>> =
        observeAll().map { all -> all.filter { it.target == target } }.distinctUntilChanged()

    override suspend fun lock(
        pkg: String,
        target: BlockTarget,
    ) = apps.update { all ->
        if (all.any { it.pkg == pkg && it.target == target }) all else all + LockedApp(pkg, target, clock.instant())
    }

    override suspend fun unlock(
        pkg: String,
        target: BlockTarget,
    ) = apps.update { all -> all.filterNot { it.pkg == pkg && it.target == target } }
}

class FakeBreakRepository(
    private val clock: Clock = TestClock(),
) : BreakRepository {
    private val session = MutableStateFlow<BreakSession?>(null)

    override fun observe(): Flow<BreakSession?> = session

    override suspend fun start(mins: Int) {
        require(mins > 0) { "mins must be positive" }
        val now = clock.instant()
        session.value = BreakSession(now, now.plusSeconds(mins * SECONDS_PER_MINUTE), mins)
    }

    override suspend fun end() {
        session.value = null
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun setOnboardingComplete(complete: Boolean) =
        state.update { it.copy(onboardingComplete = complete) }

    override suspend fun setAccountSkipped(skipped: Boolean) = state.update { it.copy(accountSkipped = skipped) }

    override suspend fun setFocusSession(session: FocusSession?) = state.update { it.copy(focusSession = session) }

    override suspend fun setAccessibilityConsentAt(at: Instant?) = state.update { it.copy(accessibilityConsentAt = at) }

    override suspend fun setAiEnabled(enabled: Boolean) = state.update { it.copy(aiEnabled = enabled) }
}
