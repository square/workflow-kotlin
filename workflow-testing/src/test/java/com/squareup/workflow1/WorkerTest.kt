/*
 * Copyright 2019 Square Inc.
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
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.squareup.workflow1

import com.squareup.workflow1.testing.test
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** Worker tests that use the [Worker.test] function. Core tests are in the core module. */
class WorkerTest {

  private class ExpectedException : RuntimeException()

  @Test fun `create emits and finishes`() {
    val worker = Worker.create {
      emit("hello")
      emit("world")
    }

    worker.test {
      assertEquals("hello", nextOutput())
      assertEquals("world", nextOutput())
      assertFinished()
    }
  }

  @Test fun `create finishes without emitting`() {
    val worker = Worker.create<String> {}

    worker.test {
      assertFinished()
    }
  }

  @Test fun `create propagates exceptions`() {
    val worker = Worker.create<Unit> { throw ExpectedException() }

    worker.test {
      assertTrue(getException() is ExpectedException)
    }
  }

  @Test fun `createSideEffect returns equivalent workers`() {
    val worker1 = Worker.createSideEffect {}
    val worker2 = Worker.createSideEffect {}

    assertNotSame(worker1, worker2)
    assertTrue(worker1.doesSameWorkAs(worker2))
  }

  @Test fun `createSideEffect runs`() {
    var ran = false
    val worker = Worker.createSideEffect {
      ran = true
    }

    worker.test {
      assertTrue(ran)
    }
  }

  @Test fun `createSideEffect finishes`() {
    val worker = Worker.createSideEffect {}

    worker.test {
      assertFinished()
    }
  }

  @Test fun `createSideEffect propagates exceptions`() {
    val worker = Worker.createSideEffect { throw ExpectedException() }

    worker.test {
      assertTrue(getException() is ExpectedException)
    }
  }

  @Test fun `from emits and finishes`() {
    val worker = Worker.from { "foo" }

    worker.test {
      assertEquals("foo", nextOutput())
      assertFinished()
    }
  }

  @Test fun `from emits null`() {
    val worker = Worker.from<String?> { null }

    worker.test {
      assertEquals(null, nextOutput())
      assertFinished()
    }
  }

  @Test fun `fromNullable emits and finishes`() {
    val worker = Worker.fromNullable { "foo" }

    worker.test {
      assertEquals("foo", nextOutput())
      assertFinished()
    }
  }

  @Test fun `fromNullable doesn't emit null`() {
    val worker = Worker.fromNullable<String> { null }

    worker.test {
      assertFinished()
    }
  }

  @Test fun `timer emits and finishes after delay`() {
    val testDispatcher = TestCoroutineDispatcher()
    val worker = Worker.timer(1000)
        // Run the timer on the test dispatcher so we can control time.
        .transform { it.flowOn(testDispatcher) }

    worker.test {
      assertNoOutput()
      assertNotFinished()

      testDispatcher.advanceTimeBy(999)
      assertNoOutput()
      assertNotFinished()

      testDispatcher.advanceTimeBy(1)
      assertEquals(Unit, nextOutput())
      assertFinished()
    }
  }
}
