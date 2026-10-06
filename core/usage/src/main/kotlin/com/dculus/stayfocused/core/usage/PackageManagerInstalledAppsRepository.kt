package com.dculus.stayfocused.core.usage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.dculus.stayfocused.core.model.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Packages of the Stay Focused apps themselves; never offered for blocking. */
internal val OwnPackages: Set<String> = setOf("com.dculus.stayfocused", "com.dculus.stayfocused.kids")

@Singleton
class PackageManagerInstalledAppsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : InstalledAppsRepository {
        override fun observeLaunchableApps(): Flow<List<AppInfo>> =
            callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context?,
                            intent: Intent?,
                        ) {
                            trySend(Unit)
                        }
                    }
                val filter =
                    IntentFilter().apply {
                        addAction(Intent.ACTION_PACKAGE_ADDED)
                        addAction(Intent.ACTION_PACKAGE_REMOVED)
                        addAction(Intent.ACTION_PACKAGE_CHANGED)
                        addDataScheme("package")
                    }
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                trySend(Unit)
                awaitClose { context.unregisterReceiver(receiver) }
            }.conflate()
                .map { queryLaunchableApps() }
                .flowOn(Dispatchers.IO)

        private fun queryLaunchableApps(): List<AppInfo> {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved: List<ResolveInfo> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, 0)
                }
            return toSortedApps(
                entries = resolved.map { it.activityInfo.packageName to it.loadLabel(pm).toString() },
                excluded = OwnPackages,
            )
        }
    }

/** Dedupes by package, drops [excluded] packages and sorts by label using the locale's collation. */
internal fun toSortedApps(
    entries: List<Pair<String, String>>,
    excluded: Set<String>,
    locale: Locale = Locale.getDefault(),
): List<AppInfo> {
    val collator = Collator.getInstance(locale)
    return entries
        .asSequence()
        .filter { (pkg, _) -> pkg !in excluded }
        .distinctBy { (pkg, _) -> pkg }
        .map { (pkg, label) -> AppInfo(pkg, label.ifBlank { pkg }) }
        .sortedWith { a, b ->
            collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.pkg.compareTo(b.pkg)
        }.toList()
}
