@file:OptIn(WorkflowExperimentalRuntime::class)
@file:Suppress("JUnitMalformedDeclaration")

package com.squareup.workflow1.android

import androidx.compose.ui.platform.AndroidUiDispatcher
import app.cash.burst.Burst
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RENDER_PER_ACTION
import com.squareup.workflow1.RuntimeConfigOptions.PARTIAL_TREE_RENDERING
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
import com.squareup.workflow1.RuntimeConfigOptions.STABLE_EVENT_HANDLERS
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderPassesComplete
import com.squareup.workflow1.WorkflowInterceptor.RuntimeLoopOutcome
import com.squareup.workflow1.action
import com.squareup.workflow1.asWorker
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.stateful
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import papa.Choreographers
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

@OptIn(WorkflowExperimentalRuntime::class)
@Burst
class AndroidDispatchersRenderWorkflowInTest(
  private val runtime: RuntimeOptions = RuntimeOptions.DEFAULT
) {

  private val orderIndex = AtomicInteger(0)

  private fun resetOrderCounter() {
    orderIndex.set(0)
  }

  @Before
  fun setup() {
    resetOrderCounter()
  }

  private fun expectInOrder(
    expected: Int,
    prefix: String = ""
  ) {
    val localActual = orderIndex.getAndIncrement()
    assertEquals(
      expected,
      localActual,
      "$prefix: This should have happened" +
        " in a different order position:"
    )
  }

  @Test
  fun conflate_renderings_for_multiple_worker_actions_same_trigger() =
    runTest {
      val trigger = MutableSharedFlow<String>()
      val renderingsConsumed = mutableListOf<String>()
      var renderingsProduced = 0
      val countInterceptor = object : WorkflowInterceptor {
        override fun onRuntimeLoopTick(outcome: RuntimeLoopOutcome) {
          if (outcome is RenderPassesComplete<*>) {
            renderingsProduced++
          }
        }
      }

      val childWorkflow = Workflow.stateful<String, String, String>(
        initialState = "unchanged state",
        render = { renderState ->
          runningWorker(
            worker = trigger.asWorker(),
            key = "Worker1"
          ) {
            action("childHandleWorker") {
              val newState = "$it+u1"
              state = newState
              setOutput(newState)
            }
          }
          renderState
        }
      )
      val workflow = Workflow.stateful<String, String, String>(
        initialState = "unchanged state",
        render = { renderState ->
          renderChild(childWorkflow) { childOutput ->
            action("childHandleOutput") {
              state = childOutput
            }
          }
          runningWorker(
            worker = trigger.asWorker(),
            key = "Worker2"
          ) {
            action("handleWorker2") {
              // Update the state in order to show conflation.
              state = "$state+u2"
            }
          }
          runningWorker(
            worker = trigger.asWorker(),
            key = "Worker3"
          ) {
            action("handleWorker3") {
              // Update the state in order to show conflation.
              state = "$state+u3"
            }
          }
          runningWorker(
            worker = trigger.asWorker(),
            key = "Worker4"
          ) {
            action("handleWorker4") {
              // Update the state in order to show conflation.
              state = "$state+u4"
              // Output only on the last one!
              setOutput(state)
            }
          }
          renderState
        }
      )
      val props = MutableStateFlow(Unit)
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope +
          AndroidUiDispatcher.Main,
        props = props,
        runtimeConfig = runtime.runtimeConfig,
        workflowTracer = null,
        interceptors = listOf(countInterceptor)
      ) { }

      val renderedCompletedUpdate = Mutex(locked = true)

      val collectionJob = launch(AndroidUiDispatcher.Main) {
        renderings.collect {
          renderingsConsumed += it
          if (it == "state change+u1+u2+u3+u4") {
            // We expect to be able to consume our final rendering *before* the end of the frame.
            expectInOrder(0)
            renderedCompletedUpdate.unlock()
          }
        }
      }

      launch(AndroidUiDispatcher.Main) {
        Choreographers.postOnFrameRendered {
          // We are expecting this to happen last, after we get the rendering!
          expectInOrder(1)
        }
        trigger.emit("state change")
      }

      renderedCompletedUpdate.lock()
      collectionJob.cancel()

      // Regardless only ever 2 renderings are consumed as the compose dispatcher drains all of
      // the coroutines to update state before the collector can consume a rendering.
      assertEquals(
        expected = 2,
        actual = renderingsConsumed.size,
        message = "Expected 2 consumed renderings."
      )
      // There are 2 attempts to produce a rendering for Conflate (initial and then the update.)
      // And otherwise there are *5* attempts to produce a new rendering.
      val expected = if (runtime.runtimeConfig.contains(CONFLATE_STALE_RENDERINGS)) 2 else 5
      assertEquals(
        expected = if (runtime.runtimeConfig.contains(CONFLATE_STALE_RENDERINGS)) 2 else 5,
        actual = renderingsProduced,
        message = "Expected $expected renderings to be produced (passed signal to interceptor)."
      )
      assertEquals(
        expected = "state change+u1+u2+u3+u4",
        actual = renderingsConsumed.last()
      )
    }

  @Test
  fun conflate_renderings_for_multiple_side_effect_actions() =
    runTest {

      val trigger = MutableSharedFlow<String>()
      val renderingsConsumed = mutableListOf<String>()
      var renderingsProduced = 0
      val countInterceptor = object : WorkflowInterceptor {
        override fun onRuntimeLoopTick(outcome: RuntimeLoopOutcome) {
          if (outcome is RenderPassesComplete<*>) {
            renderingsProduced++
          }
        }
      }

      val childWorkflow = Workflow.stateful<String, String, String>(
        initialState = "unchanged state",
        render = { renderState ->
          runningSideEffect("childSideEffect") {
            trigger.collect {
              actionSink.send(
                action(
                  name = "handleChildSideEffectAction",
                ) {
                  val newState = "$it+u1"
                  state = newState
                  setOutput(newState)
                }
              )
            }
          }
          renderState
        }
      )
      val workflow = Workflow.stateful<String, String, String>(
        initialState = "unchanged state",
        render = { renderState ->
          renderChild(childWorkflow) { childOutput ->
            action("childHandler") {
              state = childOutput
            }
          }
          runningSideEffect("parentSideEffect") {
            trigger.collect {
              actionSink.send(
                action(
                  name = "handleParentSideEffectAction",
                ) {
                  state = "$state+u2"
                }
              )
            }
          }
          renderState
        }
      )
      val props = MutableStateFlow(Unit)
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope +
          AndroidUiDispatcher.Main,
        props = props,
        runtimeConfig = runtime.runtimeConfig,
        workflowTracer = null,
        interceptors = listOf(countInterceptor)
      ) { }

      val renderedCompletedUpdate = Mutex(locked = true)

      val collectionJob = launch(AndroidUiDispatcher.Main) {
        renderings.collect {
          renderingsConsumed += it
          if (it == "state change+u1+u2") {
            // We expect to get our completed rendering consumed before the end of the frame.
            expectInOrder(0)
            renderedCompletedUpdate.unlock()
          }
        }
      }

      launch(AndroidUiDispatcher.Main) {
        Choreographers.postOnFrameRendered {
          // We are expecting this to happen last, after we get the rendering!
          expectInOrder(1)
        }
        trigger.emit("state change")
      }

      renderedCompletedUpdate.lock()
      collectionJob.cancel()

      // Regardless only ever 2 renderings are consumed as the compose dispatcher drains all of
      // the coroutines to update state before the collector can consume a rendering.
      assertEquals(
        expected = 2,
        actual = renderingsConsumed.size,
        message = "Expected 2 consumed renderings."
      )
      // There are 2 attempts to produce a rendering for Conflate (initial and then the update.)
      // And otherwise there are *3* attempts to produce a new rendering.
      val expected = if (runtime.runtimeConfig.contains(CONFLATE_STALE_RENDERINGS)) 2 else 3
      assertEquals(
        expected = if (runtime.runtimeConfig.contains(CONFLATE_STALE_RENDERINGS)) 2 else 3,
        actual = renderingsProduced,
        message = "Expected $expected renderings to be produced (passed signal to interceptor)."
      )
      assertEquals(
        expected = "state change+u1+u2",
        actual = renderingsConsumed.last()
      )
    }

  private class SimpleScreen(
    val name: String = "Empty",
    val callback: () -> Unit,
  )

  @Test
  fun all_runtimes_handle_side_effect_actions_before_the_next_frame() =
    runTest {
      val renderingUpdateComplete = Mutex(locked = true)
      val trigger = MutableSharedFlow<String>()

      val workflow = Workflow.stateful<String, String, String>(
        initialState = "unchanged state",
        render = { renderState ->
          runningSideEffect("only1") {
            trigger.collect {
              actionSink.send(action(name = "triggerCollect") { state = it })
            }
          }
          renderState
        }
      )

      // We are rendering using Compose's AndroidUiDispatcher.Main.
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope +
          AndroidUiDispatcher.Main,
        props = MutableStateFlow(Unit).asStateFlow(),
        runtimeConfig = runtime.runtimeConfig,
        workflowTracer = null,
        interceptors = emptyList()
      ) {}

      val collectionJob = launch(AndroidUiDispatcher.Main) {
        renderings.collect {
          if (it == "changed state") {
            // The rendering we were looking for!
            expectInOrder(0)
            renderingUpdateComplete.unlock()
          } else {
            Choreographers.postOnFrameRendered {
              // We are expecting this to happen last, after we get the rendering!
              expectInOrder(1)
            }
            trigger.emit("changed state")
          }
        }
      }

      renderingUpdateComplete.lock()
      collectionJob.cancel()
    }

  @Test
  fun all_runtimes_handle_rendering_events_before_next_frame() = runTest {
    val renderingUpdateComplete = Mutex(locked = true)
    val workflow = Workflow.stateful<String, String, SimpleScreen>(
      initialState = "neverends",
      render = { renderState ->
        SimpleScreen(
          name = renderState,
          callback = {
            actionSink.send(action(name = "handleInput") { state = "$state+$state" })
          }
        )
      }
    )

    val renderings = renderWorkflowIn(
      workflow = workflow,
      scope = backgroundScope +
        AndroidUiDispatcher.Main,
      props = MutableStateFlow(Unit).asStateFlow(),
      runtimeConfig = runtime.runtimeConfig,
      workflowTracer = null,
      interceptors = emptyList()
    ) {}

    val collectionJob = launch(AndroidUiDispatcher.Main) {
      renderings.collect {
        if (it.name == "neverends+neverends") {
          // The rendering we were looking for after the event!
          expectInOrder(0)
          renderingUpdateComplete.unlock()
        } else {
          Choreographers.postOnFrameRendered {
            // This should be happening last!
            expectInOrder(1)
          }
          // First rendering, lets call it.
          it.callback()
        }
      }
    }

    renderingUpdateComplete.lock()
    collectionJob.cancel()
  }
}

enum class RuntimeOptions(
  val runtimeConfig: RuntimeConfig
) {
  DEFAULT(RENDER_PER_ACTION),
  RENDER_ONLY(setOf(RENDER_ONLY_WHEN_STATE_CHANGES)),
  CONFLATE(setOf(CONFLATE_STALE_RENDERINGS)),
  STABLE(setOf(STABLE_EVENT_HANDLERS)),
  RENDER_ONLY_CONFLATE(setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES)),
  RENDER_ONLY_PARTIAL(setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING)),
  RENDER_ONLY_CONFLATE_STABLE(
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES, STABLE_EVENT_HANDLERS)
  ),
  RENDER_ONLY_PARTIAL_STABLE(
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING, STABLE_EVENT_HANDLERS)
  ),
  RENDER_ONLY_CONFLATE_PARTIAL(
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING)
  ),
  RENDER_ONLY_CONFLATE_PARTIAL_STABLE(
    setOf(
      CONFLATE_STALE_RENDERINGS,
      RENDER_ONLY_WHEN_STATE_CHANGES,
      PARTIAL_TREE_RENDERING,
      STABLE_EVENT_HANDLERS
    )
  )
}
