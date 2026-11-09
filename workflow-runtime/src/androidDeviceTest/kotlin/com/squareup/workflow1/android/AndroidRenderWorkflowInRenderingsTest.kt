package com.squareup.workflow1.android

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.StatelessWorkflow
import java.lang.ref.WeakReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import leakcanary.GcTrigger
import leakcanary.inProcess
import org.junit.Test

/**
 * Tests for the [StateFlow][kotlinx.coroutines.flow.StateFlow] of renderings returned by the
 * `SavedStateHandle` overload of [renderWorkflowIn].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AndroidRenderWorkflowInRenderingsTest {

  /**
   * Regression test: the first rendering used to stay reachable for as long as the runtime was
   * running, even after later renderings replaced it.
   */
  @Test
  fun firstRenderingIsNotRetainedAfterItIsReplaced() = runTest {
    val props = MutableStateFlow(0)
    val tracker = FirstRenderingTracker()
    // The first rendering is only ever created inside the workflow's render method, and only
    // referenced through tracker's WeakReference, so nothing in this test can keep it alive.
    val renderings =
      renderWorkflowIn(
        workflow = tracker.workflow,
        scope = backgroundScope,
        props = props,
        savedStateHandle = SavedStateHandle(),
      )
    runCurrent()

    // The runtime is still running and tracking props.
    props.value = 1
    runCurrent()
    assertThat(renderings.value.props).isEqualTo(1)

    assertThat(awaitCleared(tracker.firstRendering!!)).isTrue()
  }

  @Test
  fun firstAndLaterSnapshotsAreSaved() = runTest {
    val handle = SavedStateHandle()
    val props = MutableStateFlow(0)
    val renderings =
      renderWorkflowIn(
        workflow = FirstRenderingTracker().workflow,
        scope = backgroundScope,
        props = props,
        savedStateHandle = handle,
      )
    // Snapshots are saved by a coroutine that is dispatched, not started inline.
    assertThat(handle.contains(KEY)).isFalse()

    // The snapshot that goes with the replayed first rendering is saved too.
    runCurrent()
    val firstSnapshot = handle.get<Any>(KEY)
    assertThat(firstSnapshot).isNotNull()

    // Each snapshot is saved before its rendering is published.
    props.value = 1
    runCurrent()
    assertThat(renderings.value.props).isEqualTo(1)
    assertThat(handle.get<Any>(KEY)).isNotSameInstanceAs(firstSnapshot)
  }

  private fun awaitCleared(reference: WeakReference<*>): Boolean {
    repeat(GC_ATTEMPTS) {
      if (reference.get() == null) return true
      GcTrigger.inProcess().runGc()
    }
    return reference.get() == null
  }

  /** Not a data class, so every rendering is distinct. */
  private class CountingRendering(val props: Int)

  /** Remembers the first rendering it creates, but only weakly. */
  private class FirstRenderingTracker {
    @Volatile var firstRendering: WeakReference<CountingRendering>? = null

    val workflow =
      object : StatelessWorkflow<Int, Nothing, CountingRendering>() {
        override fun render(
          renderProps: Int,
          context: RenderContext<Int, Nothing>,
        ): CountingRendering {
          val rendering = CountingRendering(renderProps)
          if (firstRendering == null) firstRendering = WeakReference(rendering)
          return rendering
        }
      }
  }

  private companion object {
    const val GC_ATTEMPTS = 5
  }
}
