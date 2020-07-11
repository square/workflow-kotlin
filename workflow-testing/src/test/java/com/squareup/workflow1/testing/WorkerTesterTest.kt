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
package com.squareup.workflow1.testing

import com.squareup.workflow1.Worker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkerTesterTest {

  @Test fun `assertNoOutput passes after worker finishes without emitting`() {
    val worker = Worker.finished<Unit>()
    worker.test {
      assertNoOutput()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test fun `assertNoOutput fails after worker emits`() {
    val worker = Worker.from { Unit }
    val error = assertFailsWith<AssertionError> {
      worker.test {
        assertNoOutput()
      }
    }
    assertEquals("Expected no output to have been emitted.", error.message)
  }

  @Test fun `assertFinished passes when worker finishes without emitting`() {
    val worker = Worker.finished<Unit>()
    worker.test {
      assertFinished()
    }
  }

  @Test fun `assertFinished fails when worker hasn't finished and hasn't emitted`() {
    val worker = Worker.createSideEffect { suspendCancellableCoroutine {} }
    val error = assertFailsWith<AssertionError> {
      worker.test {
        assertFinished()
      }
    }
    assertEquals("Expected Worker to be finished.", error.message)
  }

  @Test fun `assertFinished fails when worker has emitted but hasn't finished`() {
    val worker = Worker.create {
      emit("output")
      suspendCancellableCoroutine {}
    }
    val error = assertFailsWith<AssertionError> {
      worker.test {
        assertFinished()
      }
    }
    assertEquals("Expected Worker to be finished. Emitted outputs: [output]", error.message)
  }

  @Test fun `assertFinished failure includes all queued outputs`() {
    val worker = Worker.create {
      emit("foo")
      emit("bar")
      suspendCancellableCoroutine {}
    }
    val error = assertFailsWith<AssertionError> {
      worker.test {
        assertFinished()
      }
    }
    assertEquals("Expected Worker to be finished. Emitted outputs: [foo, bar]", error.message)
  }
}
