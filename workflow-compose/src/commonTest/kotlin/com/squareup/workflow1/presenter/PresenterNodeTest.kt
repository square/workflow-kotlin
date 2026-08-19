package com.squareup.workflow1.presenter

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.getValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest

internal class PresenterNodeTest {

  companion object {
    val TestSlot = PresenterSlot<String>("Test")
  }

  private val testClock = BroadcastFrameClock(
    onNewAwaiters = {
      println("OMG new awaiters!")
    },
  )

  @Test
  fun emptyComposition() = runTest(testClock) {
    val slots by present(scope = backgroundScope) {
      // Empty
    }

    assertFalse(DefaultPresenterSlot in slots)
    assertFalse(TestSlot in slots)
  }

  @Test
  fun valueEmittedOnDefaultSlot() = runTest(testClock) {
    val slots by present(scope = this) {
      Presenter {
        outputSlots[defaultSlot] = "hello world"
      }
    }

    assertTrue(DefaultPresenterSlot in slots)
    assertFalse(TestSlot in slots)
    assertEquals("hello world", slots[DefaultPresenterSlot]?.resolveForTest())

    coroutineContext.job.cancelChildren()
  }

  @Test
  fun valueEmittedOnTestSlot() = runTest(testClock) {
    val slots by present(scope = backgroundScope) {
      Presenter {
        outputSlots[TestSlot] = "hello world"
      }
    }

    assertFalse(DefaultPresenterSlot in slots)
    assertTrue(TestSlot in slots)
    assertEquals("hello world", slots[TestSlot]?.resolveForTest())

    coroutineContext.job.cancelChildren()
  }
}
