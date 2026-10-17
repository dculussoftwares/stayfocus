package com.dculus.stayfocused.core.sync

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RemoteDeviceMappingTest {
    private fun event(
        id: String,
        lock: Boolean,
        at: Long?,
        pkg: String = "app.one",
        status: CommandStatus = CommandStatus.DONE,
    ) = LockEvent(id, lock, pkg, at, status)

    @Test
    fun theLastCommandForAnAppWins() {
        val events = listOf(event("c", false, 3), event("a", true, 1), event("b", true, 2))
        assertEquals(emptySet(), lockedPackages(events))
        assertEquals(setOf("app.one"), lockedPackages(events.filter { it.id != "c" }))
    }

    @Test
    fun appsAreIndependent() {
        val events =
            listOf(event("a", true, 1, "x"), event("b", true, 2, "y"), event("c", false, 3, "x"))
        assertEquals(setOf("y"), lockedPackages(events))
    }

    @Test
    fun failedAndExpiredCommandsNeverTookEffect() {
        val events =
            listOf(
                event("a", true, 1),
                event("b", false, 2, status = CommandStatus.FAILED),
                event("c", false, 3, status = CommandStatus.EXPIRED),
            )
        assertEquals(setOf("app.one"), lockedPackages(events))
    }

    @Test
    fun aPendingCommandCountsAndAnUnstampedOneIsTheNewest() {
        val events =
            listOf(
                event("a", true, 5, status = CommandStatus.PENDING),
                event("b", false, null, status = CommandStatus.PENDING),
            )
        assertEquals(emptySet(), lockedPackages(events))
        assertEquals(setOf("app.one"), lockedPackages(events.take(1)))
    }

    @Test
    fun aSwitchedOffBlockIsStillReadWhenAllowed() {
        val data = mapOf("enabled" to false, "type" to "LIMIT", "name" to "Instagram", "limitMins" to 30L)
        assertNull(parseRule("b", data))
        assertNotNull(parseRuleAnyState("b", data))
        assertEquals("Instagram", parseRuleAnyState("b", data)?.name)
    }

    @Test
    fun anUnreadableBlockIsSkippedEitherWay() {
        assertNull(parseRuleAnyState("b", mapOf("enabled" to false, "type" to "LIMIT", "name" to "No limit")))
        assertNull(parseRuleAnyState("b", mapOf("type" to "NOPE", "name" to "x")))
    }
}
