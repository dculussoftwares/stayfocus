package com.dculus.stayfocused.core.blocking

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

class AndroidForegroundEnvironment
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ForegroundEnvironment {
        override val ownPackage: String get() = context.packageName

        override fun imePackages(): Set<String> {
            val imm = context.getSystemService(InputMethodManager::class.java) ?: return emptySet()
            return imm.enabledInputMethodList.mapTo(HashSet()) { it.packageName }
        }

        override fun isActivity(
            packageName: String,
            className: String,
        ): Boolean? =
            try {
                context.packageManager.getActivityInfo(ComponentName(packageName, className), 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                // Either not an activity, or the package is not visible to us (Android 11+). We can't tell, so the
                // tracker must not drop the event: blocking an app late is worse than counting a stray window.
                null
            } catch (_: RuntimeException) {
                null
            }

        override fun nowMillis(): Long = System.currentTimeMillis()
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface ForegroundModule {
    @Binds
    @Singleton
    fun environment(impl: AndroidForegroundEnvironment): ForegroundEnvironment
}
