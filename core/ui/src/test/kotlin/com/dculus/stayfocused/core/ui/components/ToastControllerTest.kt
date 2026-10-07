package com.dculus.stayfocused.core.ui.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToastControllerTest {
    @Test
    fun autoDismissesAfter2600ms() =
        runTest {
            val controller = ToastController(this)
            controller.show("Hello")
            assertEquals("Hello", controller.message)
            advanceTimeBy(2599)
            assertEquals("Hello", controller.message)
            advanceTimeBy(2)
            assertNull(controller.message)
        }

    @Test
    fun secondMessageReplacesFirstAndRestartsTimer() =
        runTest {
            val controller = ToastController(this)
            controller.show("First")
            advanceTimeBy(2000)
            controller.show("Second")
            assertEquals("Second", controller.message)
            advanceTimeBy(2000) // 4000 ms since the first, 2000 since the second
            assertEquals("Second", controller.message)
            advanceTimeBy(601)
            assertNull(controller.message)
        }

    @Test
    fun dismissClearsImmediately() =
        runTest {
            val controller = ToastController(this)
            controller.show("Hello")
            controller.dismiss()
            assertNull(controller.message)
        }
}
