/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(
    ExperimentalCoroutinesApi::class,
    ExperimentalStdlibApi::class,
    ExperimentalWorkflowApi::class
)
class SinkTest {

  private val sink = RecordingSink()

  @Test fun `collectToActionSink sends action`() {
    runBlockingTest {
      val flow = MutableStateFlow(1)
      val collector = launch {
        flow.collectToSink(sink) {
          action {
            state = "$state $it"
            setOutput("output: $it")
          }
        }
      }

      advanceUntilIdle()
      assertEquals(1, sink.actions.size)
      sink.actions.removeFirst()
          .let { action ->
            val (newState, output) = action.applyTo("state")
            assertEquals("state 1", newState)
            assertEquals("output: 1", output?.value)
          }
      assertTrue(sink.actions.isEmpty())

      flow.value = 2
      advanceUntilIdle()
      assertEquals(1, sink.actions.size)
      sink.actions.removeFirst()
          .let { action ->
            val (newState, output) = action.applyTo("state")
            assertEquals("state 2", newState)
            assertEquals("output: 2", output?.value)
          }

      collector.cancel()
    }
  }

  @Test fun `sendAndAwaitApplication applies action`() {
    var applications = 0
    val action = action<String, String> {
      applications++
      state = "$state applied"
      setOutput("output")
    }

    runBlockingTest {
      launch { sink.sendAndAwaitApplication(action) }
      advanceUntilIdle()

      val enqueuedAction = sink.actions.removeFirst()
      val (newState, output) = enqueuedAction.applyTo("state")
      assertEquals(1, applications)
      assertEquals("state applied", newState)
      assertEquals("output", output?.value)
    }
  }

  @Test fun `sendAndAwaitApplication suspends until after applied`() {
    runBlockingTest {
      var resumed = false
      val action = action<String, String> {
        assertFalse(resumed)
      }
      launch {
        sink.sendAndAwaitApplication(action)
        resumed = true
      }
      advanceUntilIdle()
      assertFalse(resumed)
      assertEquals(1, sink.actions.size)

      val enqueuedAction = sink.actions.removeFirst()
      pauseDispatcher()
      enqueuedAction.applyTo("state")

      assertFalse(resumed)
      resumeDispatcher()
      advanceUntilIdle()
      assertTrue(resumed)
    }
  }

  @Test fun `sendAndAwaitApplication doesn't apply action when cancelled while suspended`() {
    runBlockingTest {
      var applied = false
      val action = action<String, String> {
        applied = true
        fail()
      }
      val sendJob = launch { sink.sendAndAwaitApplication(action) }
      advanceUntilIdle()
      assertFalse(applied)
      assertEquals(1, sink.actions.size)

      val enqueuedAction = sink.actions.removeFirst()
      sendJob.cancel()
      advanceUntilIdle()
      val (newState, output) = enqueuedAction.applyTo("ignored")

      assertFalse(applied)
      assertEquals("ignored", newState)
      assertNull(output)
    }
  }

  private class RecordingSink : Sink<WorkflowAction<String, String>> {
    val actions = mutableListOf<WorkflowAction<String, String>>()

    override fun send(value: WorkflowAction<String, String>) {
      actions += value
    }
  }
}
