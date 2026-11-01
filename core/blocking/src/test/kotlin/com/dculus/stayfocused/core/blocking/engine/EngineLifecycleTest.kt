package com.dculus.stayfocused.core.blocking.engine

import com.dculus.stayfocused.core.blocking.engine.EngineLifecycle.Owner
import org.junit.Test
import kotlin.test.assertEquals

class EngineLifecycleTest {
    private class FakeEngine : EngineRunner {
        var starts = 0
        var stops = 0

        override fun start() {
            starts++
        }

        override fun stop() {
            stops++
        }
    }

    private class FakeReceivers : ReceiverControl {
        var registered = 0
        var unregistered = 0

        override fun register() {
            registered++
        }

        override fun unregister() {
            unregistered++
        }
    }

    private val engine = FakeEngine()
    private val receivers = FakeReceivers()
    private val lifecycle = EngineLifecycle(engine, receivers)

    @Test fun firstOwnerStartsEngineAndReceiversOnce() {
        lifecycle.acquire(Owner.ACCESSIBILITY)
        lifecycle.acquire(Owner.STANDARD)
        lifecycle.acquire(Owner.STANDARD)
        assertEquals(1, engine.starts)
        assertEquals(1, receivers.registered)
        assertEquals(setOf(Owner.ACCESSIBILITY, Owner.STANDARD), lifecycle.owners.value)
    }

    @Test fun engineKeepsRunningUntilTheLastOwnerLeaves() {
        lifecycle.acquire(Owner.ACCESSIBILITY)
        lifecycle.acquire(Owner.STANDARD)
        lifecycle.release(Owner.ACCESSIBILITY)
        assertEquals(0, engine.stops)
        assertEquals(0, receivers.unregistered)
        lifecycle.release(Owner.STANDARD)
        assertEquals(1, engine.stops)
        assertEquals(1, receivers.unregistered)
    }

    @Test fun releaseWithoutAcquireDoesNothing() {
        lifecycle.release(Owner.STANDARD)
        assertEquals(0, engine.stops)
        assertEquals(0, receivers.unregistered)
    }

    @Test fun canStartAgainAfterStopping() {
        lifecycle.acquire(Owner.STANDARD)
        lifecycle.release(Owner.STANDARD)
        lifecycle.acquire(Owner.ACCESSIBILITY)
        assertEquals(2, engine.starts)
        assertEquals(2, receivers.registered)
    }
}
