package com.dculus.stayfocused.core.sync

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppSyncOpsTest {
    @Test
    fun unchangedAppsCostNothing() {
        assertTrue(appSyncOps(mapOf("a" to "A"), mapOf("a" to "A")).isEmpty())
    }

    @Test
    fun addsRenamesAndDeletes() {
        val ops = appSyncOps(mapOf("a" to "A", "gone" to "G"), mapOf("a" to "A2", "b" to "B"))
        assertEquals(
            setOf(AppSyncOp.Put("a", "A2"), AppSyncOp.Put("b", "B"), AppSyncOp.Delete("gone")),
            ops.toSet(),
        )
    }

    @Test
    fun trimsLongLabelsAndFallsBackToThePackage() {
        val ops = appSyncOps(emptyMap(), mapOf("long" to "x".repeat(300), "blank" to " "))
        assertEquals(
            setOf(AppSyncOp.Put("long", "x".repeat(100)), AppSyncOp.Put("blank", "blank")),
            ops.toSet(),
        )
    }
}
