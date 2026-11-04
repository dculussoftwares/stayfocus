package com.dculus.vesper.core.usage

import com.dculus.vesper.core.model.AppInfo
import kotlinx.coroutines.flow.Flow

/** The apps a person can block: everything with a launcher entry, except Vesper itself. */
interface InstalledAppsRepository {
    /** Emits the current list, then again whenever an app is installed, removed or changed. */
    fun observeLaunchableApps(): Flow<List<AppInfo>>
}
