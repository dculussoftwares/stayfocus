package com.dculus.stayfocused.core.blocking.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the [BlockingEngine] when the first owner joins and stops it when the last one leaves, so the
 * accessibility service and the standard-mode foreground service can both keep it alive without stepping on each
 * other.
 */
@Singleton
class EngineLifecycle
    @Inject
    internal constructor(
        private val engine: EngineRunner,
        private val receivers: ReceiverControl,
    ) {
        enum class Owner { ACCESSIBILITY, STANDARD }

        private val lock = Any()
        private val _owners = MutableStateFlow<Set<Owner>>(emptySet())
        val owners: StateFlow<Set<Owner>> = _owners.asStateFlow()

        fun acquire(owner: Owner) {
            synchronized(lock) {
                val before = _owners.value
                if (owner in before) return
                _owners.value = before + owner
                if (before.isEmpty()) {
                    receivers.register()
                    engine.start()
                }
            }
        }

        fun release(owner: Owner) {
            synchronized(lock) {
                val before = _owners.value
                if (owner !in before) return
                val after = before - owner
                _owners.value = after
                if (after.isEmpty()) {
                    engine.stop()
                    receivers.unregister()
                }
            }
        }
    }

/** What [EngineLifecycle] needs from the engine; [BlockingEngine] in the app, a fake in tests. */
internal interface EngineRunner {
    fun start()

    fun stop()
}

/** Registration of the broadcast receivers the engine needs while it runs. */
internal interface ReceiverControl {
    fun register()

    fun unregister()
}
