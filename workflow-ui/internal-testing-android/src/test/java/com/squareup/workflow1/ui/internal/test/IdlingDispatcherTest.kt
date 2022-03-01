package com.squareup.workflow1.ui.internal.test

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

// The SDK should be unimportant, but Robolectric 4.6.1 has shadowing issues if it isn't set.
@Config(sdk = [28])
@RunWith(RobolectricTestRunner::class)
internal class IdlingDispatcherTest {

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

    val job = launch(idler) {
      withContext(b) {

        // second -- cancel this current coroutine before it's completed.  Call yield() right away
        // because we need a suspension point in order to make the coroutine check for cancellation.
        cancel()
        yield()

        // should be unreachable
        fail("cancellation somehow didn't stop this coroutine?")
      }
    }

    // first -- nothing has actually dispatched yet, so the idler could be idle.  Use `yield()` to
    // allow the coroutine within `job` to start executing
    assertThat(idler.isIdle()).isTrue()
    yield()

    // third -- the job should actually have completed before this is called, but use `.join()` just
    // to be super sure.  If we're able to get past `.join()` that means the coroutine was
    // cancelled, so the call to `fail()` didn't happen.
    job.join()
    assertThat(idler.isIdle()).isTrue()
  }

  inner class NamedDispatcher(private val name: String) : CoroutineDispatcher() {

    override fun dispatch(
      context: CoroutineContext,
      block: Runnable
    ) {
      block.run()
    }

    override fun toString(): String = "NamedDispatcher ($name)"
  }
}
