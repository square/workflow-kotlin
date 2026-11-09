package com.squareup.workflow1.android

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.StatelessWorkflow
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import leakcanary.AndroidDetectLeaksAssert
import leakcanary.AndroidDetectLeaksInterceptor
import leakcanary.ReferenceQueueRetainedObjectTracker
import leakcanary.TriggeredDeletableObjectReporter.RetainTrigger
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
    // referenced weakly by tracker, so nothing in this test can keep it alive.
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

    // The first rendering has been replaced, so nothing should hold it any more. If anything
    // still does, this fails with the reference path to it.
    tracker.firstRenderingTrigger!!.markRetainedIfStronglyReachable()
    AndroidDetectLeaksAssert(
        detectLeaksInterceptor =
          AndroidDetectLeaksInterceptor(
            retainedObjectTracker = tracker.retainedObjectTracker,
            retainedDelayMillis = 0,
          )
      )
      .assertNoLeaks("first rendering")
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

    // A new snapshot is saved for each later rendering.
    props.value = 1
    runCurrent()
    assertThat(renderings.value.props).isEqualTo(1)
    assertThat(handle.get<Any>(KEY)).isNotSameInstanceAs(firstSnapshot)
  }

  /** Not a data class, so every rendering is distinct. */
  private class CountingRendering(val props: Int)

  /** Tracks the first rendering it creates, which LeakCanary only references weakly. */
  private class FirstRenderingTracker {
    val retainedObjectTracker =
      ReferenceQueueRetainedObjectTracker(
        clock = { SystemClock.uptimeMillis().milliseconds },
        onObjectRetainedListener = {},
      )
    var firstRenderingTrigger: RetainTrigger? = null

    val workflow =
      object : StatelessWorkflow<Int, Nothing, CountingRendering>() {
        override fun render(
          renderProps: Int,
          context: RenderContext<Int, Nothing>,
        ): CountingRendering {
          val rendering = CountingRendering(renderProps)
          if (firstRenderingTrigger == null) {
            firstRenderingTrigger =
              retainedObjectTracker.expectDeletionOnTriggerFor(rendering, "first rendering")
          }
          return rendering
        }
      }
  }
}
