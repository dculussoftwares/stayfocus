package com.dculus.stayfocused.core.data.repository

import androidx.room.Room
import com.dculus.stayfocused.core.data.db.StayFocusedDatabase
import org.robolectric.RuntimeEnvironment
import java.time.Clock

/** In-memory Room database shared by the Room-backed contract subclasses. */
internal class InMemoryDb {
    val db: StayFocusedDatabase =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
}

class RoomBlockRepositoryTest : BlockRepositoryContract() {
    private lateinit var holder: InMemoryDb

    override fun create(): BlockRepository {
        holder = InMemoryDb()
        return RoomBlockRepository(holder.db.blockDao())
    }

    override fun close() = holder.db.close()
}

class RoomLockedAppsRepositoryTest : LockedAppsRepositoryContract() {
    private lateinit var holder: InMemoryDb

    override fun create(clock: Clock): LockedAppsRepository {
        holder = InMemoryDb()
        return RoomLockedAppsRepository(holder.db.lockedAppDao(), clock)
    }

    override fun close() = holder.db.close()
}
