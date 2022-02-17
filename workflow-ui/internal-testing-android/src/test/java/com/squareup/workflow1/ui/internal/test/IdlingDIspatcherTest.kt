package com.squareup.workflow1.ui.internal.test

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
class IdlingDIspatcherTest {

  @Test fun `should not go to idle when dispatching to another dispatcher`() = runBlocking {

    val a = NamedDispatcher("a")
    val b = NamedDispatcher("b")

    val idler = IdlingDispatcher(a)

    assertThat(idler.isIdle()).isTrue()

    withContext(idler) {
      assertThat(idler.isIdle()).isFalse()
      assertThat(coroutineContext[ContinuationInterceptor]).isEqualTo(idler)

      withContext(b) {
        assertThat(idler.isIdle()).isFalse()
        assertThat(coroutineContext[ContinuationInterceptor]).isEqualTo(b)
      }

      assertThat(idler.isIdle()).isFalse()
      assertThat(coroutineContext[ContinuationInterceptor]).isEqualTo(idler)
    }

    assertThat(idler.isIdle()).isTrue()
  }

  @Test fun `should return to idle if a coroutine is cancelled before completion`() = runBlocking {

    val a = NamedDispatcher("a")
    val b = NamedDispatcher("b")

    val idler = IdlingDispatcher(a)

    // This will never be completed
    val lock = CompletableDeferred<Unit>()

    assertThat(idler.isIdle()).isTrue()

    val job = launch(idler) {
      withContext(b) {
        lock.await()
        fail("unreachable")
      }
    }

    // ensure that the launched job has a chance to dispatch
    yield()

    job.cancel()
    assertThat(idler.isIdle()).isTrue()
  }

  inner class NamedDispatcher(private val name: String) : CoroutineDispatcher() {

    override fun dispatch(
      context: CoroutineContext,
      block: Runnable
    ) {
      block.run()
    }

    override fun toString(): String = "RecordingDispatcher ($name)"
  }
}
