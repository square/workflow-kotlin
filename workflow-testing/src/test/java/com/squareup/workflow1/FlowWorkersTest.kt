@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.squareup.workflow1

import com.squareup.workflow1.testing.test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowWorkersTest {

  private class ExpectedException : RuntimeException()

  private val subject = Channel<String>(capacity = 1)
  private var source = flow { subject.consumeEach { emit(it) } }

  private val worker by lazy { source.asWorker() }

  @Test fun `flow emits`() {
    worker.test {
      subject.send("foo")
      assertEquals("foo", nextOutput())

      subject.send("bar")
      assertEquals("bar", nextOutput())
    }
  }

  @Test fun `flow finishes`() {
    worker.test {
      subject.close()
      assertFinished()
    }
  }

  @Test fun `flow finishes after emitting interleaved`() {
    worker.test {
      subject.send("foo")
      assertEquals("foo", nextOutput())

      subject.close()
      assertFinished()
    }
  }

  @Test fun `flow finishes after emitting grouped`() {
    worker.test {
      subject.send("foo")
      subject.close()

      assertEquals("foo", nextOutput())
      assertFinished()
    }
  }

  @Test fun `flow throws`() {
    worker.test {
      subject.close(ExpectedException())
      assertTrue(getException() is ExpectedException)
    }
  }

  @Test fun `flow is collected lazily`() {
    var collections = 0
    source = source.onCollect { collections++ }

    assertEquals(0, collections)

    worker.test {
      assertEquals(1, collections)
    }
  }

  @Test fun `flow is cancelled when worker cancelled`() {
    var cancellations = 0
    source = source.onCancel { cancellations++ }

    assertEquals(0, cancellations)

    worker.test {
      assertEquals(0, cancellations)
      cancelWorker()
      assertEquals(1, cancellations)
    }
  }

  private fun <T> Flow<T>.onCollect(action: suspend () -> Unit) = flow {
    action()
    collect { emit(it) }
  }

  private fun <T> Flow<T>.onCancel(action: suspend () -> Unit) = flow {
    try {
      collect { emit(it) }
    } catch (e: CancellationException) {
      action()
      throw e
    }
  }
}
