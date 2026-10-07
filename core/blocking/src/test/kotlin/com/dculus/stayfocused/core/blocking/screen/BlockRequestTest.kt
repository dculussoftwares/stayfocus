package com.dculus.stayfocused.core.blocking.screen

import android.app.Application
import android.content.Intent
import com.dculus.stayfocused.core.blocking.R
import com.dculus.stayfocused.core.blocking.evaluator.BlockReason
import com.dculus.stayfocused.core.blocking.evaluator.Decision
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockRequestTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `request survives the intent round trip`() {
        val request =
            BlockRequest(
                "com.example",
                "Example",
                Decision.Block(BlockReason.SCHEDULE, "b9", Instant.ofEpochMilli(42)),
                token = 5L,
            )
        assertEquals(request, BlockRequest.from(request.toIntent(context)))
    }

    @Test
    fun `manual lock without end keeps a null end`() {
        val request = BlockRequest("com.example", null, Decision.Block(BlockReason.MANUAL_LOCK, null, null))
        assertEquals(request, BlockRequest.from(request.toIntent(context)))
    }

    @Test
    fun `tracker ignores the real block activity class`() {
        assertEquals(
            BlockActivity::class.java.name,
            com.dculus.stayfocused.core.blocking.ForegroundAppTracker.BLOCK_SCREEN_CLASS,
        )
    }

    @Test
    fun `stale tokens are rejected`() {
        BlockScreenState.activeToken = 7L
        assertEquals(true, BlockScreenState.accepts(7L))
        assertEquals(true, BlockScreenState.accepts(0L))
        assertEquals(false, BlockScreenState.accepts(6L))
        BlockScreenState.activeToken = 0L
    }

    @Test
    fun `malformed intents are rejected`() {
        assertNull(BlockRequest.from(null))
        assertNull(BlockRequest.from(Intent()))
        assertNull(BlockRequest.from(Intent().putExtra("pkg", "p").putExtra("reason", "NOPE")))
    }

    @Test
    fun `every reason has copy`() {
        BlockReason.entries.forEach { assertNotNull(context.getString(reasonRes(it)).takeIf(String::isNotBlank)) }
        assertEquals("Go to home screen", context.getString(R.string.block_go_home))
    }
}
