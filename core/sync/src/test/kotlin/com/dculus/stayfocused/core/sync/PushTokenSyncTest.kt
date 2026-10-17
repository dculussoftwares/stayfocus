package com.dculus.stayfocused.core.sync

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class PushTokenSyncTest {
    private val auth = FakeAuthRepository()
    private val tokens = FakePushTokenRepository()
    private val sync = PushTokenSync(auth, tokens)

    @Test
    fun registersTheTokenOnSignInAndNotWhileSignedOut() =
        runTest(UnconfinedTestDispatcher()) {
            val job: Job = launch { sync.run() }
            assertEquals(emptyList(), tokens.registered)
            auth.signInWithEmail("p@example.com", "pw")
            assertEquals(listOf("fake"), tokens.registered)
            auth.signOut()
            assertEquals(listOf("fake"), tokens.registered)
            job.cancel()
        }

    @Test
    fun aNewTokenIsStoredForTheSignedInParentOnly() =
        runTest {
            sync.refresh()
            assertEquals(emptyList(), tokens.registered)
            auth.signInWithEmail("p@example.com", "pw")
            sync.refresh()
            assertEquals(listOf("fake"), tokens.registered)
        }
}
