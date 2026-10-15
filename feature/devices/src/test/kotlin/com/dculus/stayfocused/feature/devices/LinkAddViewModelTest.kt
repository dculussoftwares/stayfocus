package com.dculus.stayfocused.feature.devices

import app.cash.turbine.test
import com.dculus.stayfocused.core.sync.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LinkAddViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun signedOutUntilAnAccountExistsThenSignedIn() =
        runTest {
            val auth = FakeAuthRepository()
            val viewModel = LinkAddViewModel(auth)
            viewModel.state.test {
                var item = awaitItem()
                if (item == LinkAddState.Loading) item = awaitItem()
                assertEquals(LinkAddState.SignedOut, item)
                auth.signInWithEmail("parent@example.com", "secret1")
                assertEquals(LinkAddState.SignedIn, awaitItem())
                auth.signOut()
                assertEquals(LinkAddState.SignedOut, awaitItem())
            }
        }
}
