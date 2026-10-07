package com.dculus.stayfocused.core.data.repository

import com.dculus.stayfocused.core.data.db.BlockDao
import com.dculus.stayfocused.core.data.db.LockedAppDao
import com.dculus.stayfocused.core.data.db.toAppEntities
import com.dculus.stayfocused.core.data.db.toEntity
import com.dculus.stayfocused.core.data.db.toKey
import com.dculus.stayfocused.core.data.db.toModel
import com.dculus.stayfocused.core.model.Block
import com.dculus.stayfocused.core.model.BlockTarget
import com.dculus.stayfocused.core.model.LockedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

internal class RoomBlockRepository
    @Inject
    constructor(
        private val dao: BlockDao,
    ) : BlockRepository {
        override fun observeByTarget(target: BlockTarget): Flow<List<Block>> =
            dao.observeByTarget(target.toKey()).map { rows -> rows.map { it.toModel() } }

        override fun observe(id: String): Flow<Block?> = dao.observe(id).map { it?.toModel() }

        override suspend fun get(id: String): Block? = observe(id).first()

        override suspend fun upsert(block: Block) = dao.upsert(block.toEntity(), block.toAppEntities())

        override suspend fun setEnabled(
            id: String,
            enabled: Boolean,
        ) = dao.setEnabled(id, enabled)

        override suspend fun delete(id: String) = dao.delete(id)
    }

internal class RoomLockedAppsRepository
    @Inject
    constructor(
        private val dao: LockedAppDao,
        private val clock: Clock,
    ) : LockedAppsRepository {
        override fun observeAll(): Flow<List<LockedApp>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

        override fun observeByTarget(target: BlockTarget): Flow<List<LockedApp>> =
            dao.observeByTarget(target.toKey()).map { rows -> rows.map { it.toModel() } }

        override suspend fun lock(
            pkg: String,
            target: BlockTarget,
        ) = dao.insertIfAbsent(LockedApp(pkg, target, clock.instant()).toEntity())

        override suspend fun unlock(
            pkg: String,
            target: BlockTarget,
        ) = dao.delete(pkg, target.toKey())
    }
