package com.dculus.stayfocused.core.sync

import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkHandshakeMappingTest {
    // FirebaseFunctionsException cannot be built outside the SDK, so its `details.reason` mapping is covered by the
    // emulator test of the functions (firebase/functions/test/linkHandshake.test.ts) and the manual two-device check.

    @Test
    fun connectivityProblemsAreNetworkFailures() {
        assertEquals(LinkFailure.Network, RuntimeException("x", IOException("no route")).toLinkFailure())
    }

    @Test
    fun theServersReasonMapsToAFailure() {
        assertEquals(LinkFailure.Invalid, reasonToFailure("invalid"))
        assertEquals(LinkFailure.Expired, reasonToFailure("expired"))
        assertEquals(LinkFailure.Used, reasonToFailure("used"))
        assertEquals(LinkFailure.NotClaimed, reasonToFailure("not_claimed"))
        assertNull(reasonToFailure("something-new"))
        assertNull(reasonToFailure(null))
    }

    @Test
    fun anythingElseIsOther() {
        assertEquals(LinkFailure.Other, IllegalStateException("x").toLinkFailure())
    }

    @Test
    fun parsesTheClaimResponse() {
        val link =
            mapOf("code" to "482913", "model" to "Pixel 7", "deviceId" to "d1", "expiresAtMillis" to 1_700_000_000_000L)
                .toClaimedLink()
        assertEquals(ClaimedLink("482913", "Pixel 7", "d1", 1_700_000_000_000L), link)
        assertNull(mapOf("code" to "482913").toClaimedLink())
    }

    @Test
    fun parsesTheApproveResponse() {
        assertEquals(
            ApproveOutcome.Linked("p1", "d1", "Asha"),
            mapOf("linked" to true, "parentUid" to "p1", "deviceId" to "d1", "parentName" to "Asha").toApproveOutcome(),
        )
        assertEquals(ApproveOutcome.Declined, mapOf("linked" to false).toApproveOutcome())
        assertNull(mapOf("linked" to true).toApproveOutcome())
        assertNull(emptyMap<String, Any>().toApproveOutcome())
    }
}
