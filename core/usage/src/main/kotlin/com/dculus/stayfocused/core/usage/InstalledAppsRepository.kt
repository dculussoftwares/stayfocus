package com.dculus.stayfocused.core.usage

import com.dculus.stayfocused.core.model.AppInfo
import kotlinx.coroutines.flow.Flow

/** The apps a person can block: everything with a launcher entry, except Stay Focused itself. */
interface InstalledAppsRepository {
    /** Emits the current list, then again whenever an app is installed, removed or changed. */
    fun observeLaunchableApps(): Flow<List<AppInfo>>
}
