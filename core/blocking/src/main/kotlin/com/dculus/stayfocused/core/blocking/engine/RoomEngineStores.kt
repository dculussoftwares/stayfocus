package com.dculus.stayfocused.core.blocking.engine

import com.dculus.stayfocused.core.blocking.evaluator.CycleKey
import com.dculus.stayfocused.core.blocking.evaluator.CycleState
import com.dculus.stayfocused.core.data.db.BlockEventDao
import com.dculus.stayfocused.core.data.db.BlockEventEntity
import com.dculus.stayfocused.core.data.db.CycleStateDao
import com.dculus.stayfocused.core.data.db.CycleStateEntity
import java.time.Instant
import javax.inject.Inject

class RoomCycleStateStore
    @Inject
    constructor(
        private val dao: CycleStateDao,
    ) : CycleStateStore {
        override suspend fun load(key: CycleKey): CycleState? =
            dao.get(key.blockId, key.pkg)?.let { CycleState(it.usedMs, it.lockedUntil) }

        override suspend fun save(
            key: CycleKey,
            state: CycleState,
            now: Instant,
        ) {
            // A window starts when the first foreground time is counted after a reset.
            val previous = dao.get(key.blockId, key.pkg)
            val windowStart =
                when {
                    // Resting: the window that ended keeps its start.
                    state.lockedUntil != null -> previous?.windowStartedAt ?: now

                    // First counted interval after a rest starts a new window.
                    previous?.lockedUntil != null -> now

                    state.usedMs == 0L -> now

                    else -> previous?.windowStartedAt ?: now
                }
            dao.upsert(CycleStateEntity(key.blockId, key.pkg, state.usedMs, windowStart, state.lockedUntil))
        }
    }

class RoomBlockEventLog
    @Inject
    constructor(
        private val dao: BlockEventDao,
    ) : BlockEventLog {
        override suspend fun record(
            pkg: String,
            blockId: String?,
            reason: String,
            at: Instant,
        ) {
            dao.insert(BlockEventEntity(pkg = pkg, blockId = blockId, reason = reason, at = at))
        }
    }
