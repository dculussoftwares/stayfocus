package com.dculus.stayfocused.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Replaces `Dispatchers.Main` for the duration of a test so ViewModels using `viewModelScope` can run on the JVM. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

private val DEFAULT_START: Instant = Instant.parse("2023-11-14T22:13:20Z")

/** A [Clock] that only moves when the test says so. */
class TestClock(
    start: Instant = DEFAULT_START,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    @Volatile
    private var now: Instant = start

    fun set(instant: Instant) {
        now = instant
    }

    fun advance(by: Duration) {
        now = now.plus(by)
    }

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = TestClock(now, zone)

    override fun instant(): Instant = now
}
