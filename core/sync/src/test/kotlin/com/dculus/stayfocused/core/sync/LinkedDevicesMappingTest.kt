package com.dculus.stayfocused.core.sync

import com.dculus.stayfocused.core.model.TamperKind
import com.dculus.stayfocused.core.model.UnlockRequestStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedDevicesMappingTest {
    private val now = Instant.parse("2026-10-17T10:00:00Z")

    private fun ts(at: Instant) = Timestamp(at.epochSecond, 0)

    private fun raw(
        data: Map<String, Any?>,
        id: String = "d1",
    ) = RawDevice(id, data, emptyList(), emptyList(), 30)

    @Test
    fun onlineMeansLastSeenWithinThreeMinutes() {
        assertTrue(isOnline(now.minusSeconds(179), now))
        assertTrue(isOnline(now.minusSeconds(180), now))
        assertFalse(isOnline(now.minusSeconds(181), now))
        assertFalse(isOnline(null, now))
        assertTrue(isOnline(now.plusSeconds(120), now), "a parent clock that runs behind is tolerated")
        assertFalse(isOnline(now.plusSeconds(3600), now), "a far-future timestamp is not online")
    }

    @Test
    fun aPhoneIsLongOfflineAfterADayWithoutAReport() {
        assertFalse(isLongOffline(now.minus(Duration.ofHours(24)).plusSeconds(1), now))
        assertTrue(isLongOffline(now.minus(Duration.ofHours(24)), now))
        assertTrue(isLongOffline(now.minus(Duration.ofDays(9)), now))
        assertFalse(isLongOffline(null, now), "a phone that never reported is not flagged")
        assertTrue(raw(mapOf("lastSeen" to ts(now.minus(Duration.ofHours(30))))).toLinkedDevice(now).longOffline)
        assertFalse(raw(mapOf("lastSeen" to ts(now.minusSeconds(30)))).toLinkedDevice(now).longOffline)
    }

    @Test
    fun aGoodbyeWriteTakesAFreshPhoneOffline() {
        val device = raw(mapOf("online" to false, "lastSeen" to ts(now.minusSeconds(5)))).toLinkedDevice(now)
        assertFalse(device.online)
    }

    @Test
    fun theChildsOwnOnlineFlagDoesNotMakeAStalePhoneOnline() {
        val device = raw(mapOf("online" to true, "lastSeen" to ts(now.minusSeconds(600)))).toLinkedDevice(now)
        assertFalse(device.online)
    }

    @Test
    fun mapsTheDeviceDocument() {
        val device =
            raw(
                mapOf(
                    "name" to "Aarav's phone",
                    "model" to "Redmi",
                    "battery" to 140L,
                    "currentApp" to "YouTube",
                    "lastSeen" to ts(now.minusSeconds(30)),
                    "focusEndsAt" to ts(now.plusSeconds(600)),
                ),
            ).toLinkedDevice(now)
        assertEquals("Aarav's phone", device.name)
        assertEquals(100, device.battery)
        assertEquals("YouTube", device.currentApp)
        assertTrue(device.online)
        assertEquals(now.plusSeconds(600), device.focusEndsAt)
        assertEquals(30, device.todayMins)
    }

    @Test
    fun missingFieldsFallBackSafely() {
        val device = raw(mapOf("name" to " ", "currentApp" to "")).toLinkedDevice(now)
        assertEquals("d1", device.name)
        assertNull(device.battery)
        assertNull(device.currentApp)
        assertNull(device.lastSeen)
        assertFalse(device.online)
    }

    @Test
    fun parsesAlertsAndSkipsUnknownKinds() {
        val a =
            alertFrom("a", mapOf("kind" to "permission_lost", "permission" to "accessibility", "dismissed" to false))
        assertEquals(TamperKind.PERMISSION_LOST, a?.kind)
        assertEquals("accessibility", a?.permission)
        assertEquals(TamperKind.UNLINKED, alertFrom("b", mapOf("kind" to "unlinked"))?.kind)
        assertNull(alertFrom("c", mapOf("kind" to "bogus")))
    }

    @Test
    fun parsesRequestsAndSkipsMalformedOnes() {
        val r = requestFrom("r", mapOf("app" to "YouTube", "minutes" to 15L, "status" to "pending"))
        assertEquals(UnlockRequestStatus.PENDING, r?.status)
        assertEquals(15, r?.minutes)
        assertNull(requestFrom("x", mapOf("app" to "YouTube", "status" to "pending")))
        assertNull(requestFrom("y", mapOf("app" to "YouTube", "minutes" to 5L, "status" to "weird")))
    }

    @Test
    fun oldestLinkComesFirstAndUnstampedLast() {
        val sorted =
            listOf(
                raw(emptyMap(), "c"),
                raw(mapOf("linkedAt" to ts(now)), "b"),
                raw(mapOf("linkedAt" to ts(now.minusSeconds(60))), "a"),
            ).oldestLinkFirst()
        assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
    }

    @Test
    fun onlyPermanentFirestoreErrorsStopTheRetries() {
        fun error(code: FirebaseFirestoreException.Code) = FirebaseFirestoreException("x", code)
        assertTrue(error(FirebaseFirestoreException.Code.PERMISSION_DENIED).isPermanent())
        assertTrue(error(FirebaseFirestoreException.Code.UNAUTHENTICATED).isPermanent())
        assertFalse(error(FirebaseFirestoreException.Code.UNAVAILABLE).isPermanent())
        assertFalse(java.io.IOException("offline").isPermanent())
    }
}
