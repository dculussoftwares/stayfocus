package com.dculus.stayfocused.core.sync

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Declares the (initially empty) set of command handlers; M8-03 and M8-05 add theirs with `@IntoSet`. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class CommandModule {
    @Multibinds
    abstract fun handlers(): Set<@JvmSuppressWildcards CommandHandler>
}
