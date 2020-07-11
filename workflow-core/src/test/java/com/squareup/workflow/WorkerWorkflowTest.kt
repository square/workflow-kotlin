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

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkerWorkflowTest {

  /**
   * This should be impossible, since the return type is non-nullable. However it is very easy to
   * accidentally create a mock using libraries like Mockito in unit tests that return null Flows.
   */
  @Test fun `runWorker throws when flow is null`() {
    val nullFlowWorker = NullFlowWorker()

    val error = runBlocking {
      assertFailsWith<NullPointerException> {
        runWorker(nullFlowWorker, "", NoopSink)
      }
    }

    assertEquals(
        "Worker NullFlowWorker.toString returned a null Flow. " +
            "If this is a test mock, make sure you mock the run() method!",
        error.message
    )
  }

  @Test fun `runWorker coroutine is named without key`() {
    val worker = CoroutineNameWorker()
    runBlocking {
      runWorker(worker, renderKey = "", actionSink = NoopSink)
    }

    assertEquals("CoroutineNameWorker.toString", worker.recordedName)
  }

  @Test fun `runWorker coroutine is named with key`() {
    val worker = CoroutineNameWorker()
    runBlocking {
      runWorker(worker, renderKey = "foo", actionSink = NoopSink)
    }

    assertEquals("CoroutineNameWorker.toString:foo", worker.recordedName)
  }

  private object NoopSink : Sink<Any?> {
    override fun send(value: Any?) {
      // Noop
    }
  }

  private class CoroutineNameWorker : Worker<String> {
    var recordedName: String? = null
      private set

    override fun run(): Flow<String> = flow {
      recordedName = (coroutineContext[CoroutineName] as CoroutineName).name
    }

    override fun toString(): String = "CoroutineNameWorker.toString"
  }
}
