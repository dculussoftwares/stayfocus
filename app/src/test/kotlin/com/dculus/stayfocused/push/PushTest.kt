package com.dculus.stayfocused.push

import com.dculus.stayfocused.core.sync.DeviceTarget
import com.dculus.stayfocused.core.sync.FakeAuthRepository
import com.dculus.stayfocused.core.sync.FakeRequestDecisionRepository
import com.dculus.stayfocused.core.sync.RequestDecisionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushMessageTest {
    private val request =
        mapOf(
            "type" to "request",
            "deviceId" to "dev1",
            "reqId" to "r1",
            "device" to "Aarav's phone",
            "app" to "Instagram",
            "minutes" to "30",
        )

    @Test
    fun parsesARequest() {
        assertEquals(PushMessage.Request("dev1", "r1", "Aarav's phone", "Instagram", 30), PushMessage.parse(request))
    }

    @Test
    fun parsesAnUnlink() {
        val data = mapOf("type" to "unlinked", "deviceId" to "dev1", "device" to "Aarav's phone")
        assertEquals(PushMessage.Unlinked("dev1", "Aarav's phone"), PushMessage.parse(data))
    }

    @Test
    fun ignoresMalformedMessages() {
        assertNull(PushMessage.parse(emptyMap()))
        assertNull(PushMessage.parse(request + ("type" to "other")))
        assertNull(PushMessage.parse(request + ("minutes" to "0")))
        assertNull(PushMessage.parse(request + ("minutes" to "999")))
        assertNull(PushMessage.parse(request + ("minutes" to "soon")))
        assertNull(PushMessage.parse(request + ("reqId" to "../x")))
        assertNull(PushMessage.parse(request - "app"))
        assertNull(PushMessage.parse(request + ("device" to "  ")))
    }

    @Test
    fun theNotificationIdIsStablePerRequestAndPositive() {
        assertEquals(PushNotifier.notificationId("r1"), PushNotifier.notificationId("r1"))
        assertTrue(PushNotifier.notificationId("anything") > 0)
    }

    @Test
    fun minutesAreLabelledLikeThePrototype() {
        assertEquals(listOf("15m", "30m", "1h"), NOTIFICATION_APPROVALS.map(::minutesLabel))
    }
}

class RequestAnswererTest {
    private val auth = FakeAuthRepository()
    private val decisions = FakeRequestDecisionRepository()
    private val answer = RequestAnswer("dev1", "r1", 30)

    @Test
    fun recordsTheDecisionForTheSignedInParent() =
        runTest {
            auth.signInWithEmail("p@example.com", "pw")
            assertTrue(RequestAnswerer(auth, decisions).answer(answer))
            assertEquals(listOf("r1" to 30), decisions.decisions)
        }

    @Test
    fun doesNothingWhenNobodyIsSignedIn() =
        runTest {
            assertFalse(RequestAnswerer(auth, decisions).answer(answer))
            assertEquals(emptyList(), decisions.decisions)
        }

    @Test
    fun reportsARefusedDecision() =
        runTest {
            auth.signInWithEmail("p@example.com", "pw")
            decisions.failing = true
            assertFalse(RequestAnswerer(auth, decisions).answer(answer))
        }

    @Test
    fun treatsNoAnswerInTimeAsHandedOverBecauseFirestoreKeepsTheWrite() =
        runTest {
            auth.signInWithEmail("p@example.com", "pw")
            val never = CompletableDeferred<Unit>()
            val hanging =
                object : RequestDecisionRepository {
                    override suspend fun decide(
                        target: DeviceTarget,
                        reqId: String,
                        minutes: Int,
                    ) = never.await()
                }
            assertTrue(RequestAnswerer(auth, hanging).answer(answer, timeoutMillis = 1_000))
        }
}
