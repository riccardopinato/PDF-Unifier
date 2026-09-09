package com.example

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PdfOcrControllerTest {
    @Test fun keepsSourcePageAndIgnoresRepeatedStartWhileRunning() = runTest {
        var calls = 0
        val controller = PdfOcrController(this) { page ->
            calls++
            delay(100)
            "Text from $page"
        }
        controller.start(2)
        controller.start(7)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(2, controller.ui.page)
        assertEquals("Text from 2", controller.ui.text)
        assertFalse(controller.ui.running)
    }

    @Test fun dismissedWorkCannotReplaceNewResultAndDoesNotOverlap() = runTest {
        var active = 0
        var maximum = 0
        val controller = PdfOcrController(this) { page ->
            active++
            maximum = maxOf(maximum, active)
            try {
                withContext(NonCancellable) { delay(100) }
                "Result $page"
            } finally { active-- }
        }
        controller.start(0)
        runCurrent()
        controller.dismiss()
        assertFalse(controller.ui.visible)
        assertNull(controller.ui.text)
        controller.start(1)
        advanceUntilIdle()
        assertEquals(1, maximum)
        assertEquals(1, controller.ui.page)
        assertEquals("Result 1", controller.ui.text)
    }

    @Test fun failureCanBeRetriedAndEmptyTextIsNotAnError() = runTest {
        var first = true
        val controller = PdfOcrController(this) {
            if (first) { first = false; throw IllegalStateException("failed") }
            ""
        }
        controller.start(0)
        advanceUntilIdle()
        assertTrue(controller.ui.failed)
        assertFalse(controller.ui.running)
        controller.start(0)
        advanceUntilIdle()
        assertFalse(controller.ui.failed)
        assertEquals("", controller.ui.text)
    }
}
