package com.dculus.vesper.core.blocking.standard

import com.dculus.vesper.core.blocking.engine.BlockingEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StandardModule {
    @Binds
    abstract fun activeRules(impl: BlockingEngine): ActiveRules

    @Binds
    abstract fun support(impl: AndroidStandardSupport): StandardSupport

    @Binds
    abstract fun screen(impl: AndroidInteractiveScreen): InteractiveScreen
}
