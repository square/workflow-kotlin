@file:OptIn(WorkflowExperimentalRuntime::class)
@file:Suppress("JUnitMalformedDeclaration")

package com.squareup.workflow1.android

import androidx.compose.ui.platform.AndroidUiDispatcher
import app.cash.burst.Burst
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions.DEFAULT
import com.squareup.workflow1.RuntimeConfigOptions.DRAIN_EXCLUSIVE_ACTIONS
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderingProduced
import com.squareup.workflow1.WorkflowInterceptor.RuntimeUpdate
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
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
  private val runtime: RuntimeOptions = DEFAULT
) {

  private val trigger = MutableSharedFlow<String>()
  private val renderingsConsumed = mutableListOf<String>()
  private var renderingsProduced = 0
  private var renderPasses = 0
  private val countingInterceptor = object : WorkflowInterceptor {
    override fun onRuntimeUpdate(update: RuntimeUpdate) {
      if (update is RenderingProduced<*>) {
        renderingsProduced++
      }
    }

    override fun <P, R> onRenderAndSnapshot(
      renderProps: P,
      proceed: (P) -> RenderingAndSnapshot<R>,
      session: WorkflowSession
    ): RenderingAndSnapshot<R> {
      renderPasses++
      return proceed(renderProps)
    }
  }
  private val orderIndex = AtomicInteger(0)

  private fun resetCounters() {
    renderingsConsumed.clear()
    renderingsProduced = 0
    renderPasses = 0
    orderIndex.set(0)
  }

  @Before
  fun setup() {
    resetCounters()
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

  private fun runtimeOptimizationsTestHarness(
    workflow: Workflow<Unit, *, String>,
    targetRendering: String,
    expectedRenderPasses: Int,
    expectedRenderingsProduced: Int,
    expectedRenderingsConsumed: Int
  ) = runTest {
    val props = MutableStateFlow(Unit)
    val renderings = renderWorkflowIn(
      workflow = workflow,
      scope = backgroundScope +
        AndroidUiDispatcher.Main,
      props = props,
      runtimeConfig = runtime.runtimeConfig,
      workflowTracer = null,
      interceptors = listOf(countingInterceptor)
    ) { }

    val targetRenderingReceived = Mutex(locked = true)

    val collectionJob = launch(AndroidUiDispatcher.Main) {
      renderings.collect {
        renderingsConsumed += it
        if (it == targetRendering) {
          // We expect to be able to consume our final rendering *before* the end of the frame.
          expectInOrder(0)
          targetRenderingReceived.unlock()
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

    targetRenderingReceived.lock()
    collectionJob.cancel()

    assertEquals(
      expected = expectedRenderPasses,
      actual = renderPasses,
      message = "Expected $expectedRenderPasses render passes."
    )
    assertEquals(
      expected = expectedRenderingsConsumed,
      actual = renderingsConsumed.size,
      message = "Expected $expectedRenderingsConsumed consumed renderings."
    )
    assertEquals(
      expected = expectedRenderingsProduced,
      actual = renderingsProduced,
      message = "Expected $expectedRenderingsProduced renderings to be produced" +
        " (passed signal to interceptor)."
    )
    assertEquals(
      expected = targetRendering,
      actual = renderingsConsumed.last()
    )
  }

  @Test
  fun optimizations_for_multiple_worker_actions_same_trigger() {
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
    runtimeOptimizationsTestHarness(
      workflow = workflow,
      targetRendering = "state change+u1+u2+u3+u4",
      // There are 5 render passes the actions all update the same state.
      expectedRenderPasses = 5,
      // There are 2 attempts to produce a rendering for Conflate (initial and then the update.)
      // And otherwise there are *5* attempts to produce a new rendering.
      expectedRenderingsProduced =
      if (runtime.runtimeConfig.contains(CONFLATE_STALE_RENDERINGS)) 2 else 5,
      // Regardless only ever 2 renderings are consumed as the compose dispatcher drains all of
      // the coroutines to update state before the collector can consume a rendering.
      expectedRenderingsConsumed = 2
    )
  }

  @Test
  fun optimizations_for_multiple_side_effect_actions() {

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
    runtimeOptimizationsTestHarness(
      workflow = workflow,
      targetRendering = "state change+u1+u2",
      // There are 3 render passes as the actions all update the same state.
      expectedRenderPasses = 3,
      // There are 2 attempts to produce a rendering for Conflate (initial and then the update.)
      // And otherwise there are *3* attempts to produce a new rendering.
      expectedRenderingsProduced =
      if (runtime.runtimeConfig.contains(CONFLATE_STALE_RENDERINGS)) 2 else 3,
      // Regardless only ever 2 renderings are consumed as the compose dispatcher drains all of
      // the coroutines to update state before the collector can consume a rendering.
      expectedRenderingsConsumed = 2
    )
  }

  @Test
  fun optimizations_for_exclusive_actions() {

    val childWorkflow = Workflow.stateful<String, String, String>(
      initialState = "unchanged state",
      render = { renderState ->
        runningWorker(
          worker = trigger.asWorker(),
          key = "Worker 1"
        ) {
          action("handleWorker1Output") {
            state = "$it+u1"
            setOutput("$it+u1")
          }
        }
        renderState
      }
    )
    val workflow = Workflow.stateful<String, String, String>(
      initialState = "unchanged state",
      render = { renderState ->
        renderChild(childWorkflow, key = "key1") { _ ->
          WorkflowAction.noAction()
        }
        renderChild(childWorkflow, key = "key2") { output ->
          action(name = "child2Handler") {
            state = output
          }
        }
        renderState
      }
    )

    runtimeOptimizationsTestHarness(
      workflow = workflow,
      targetRendering = "state change+u1",
      // 2 for DEA (initial synchronous + 1 for the update); 3 otherwise given the 2 child actions.
      expectedRenderPasses = if (runtime.runtimeConfig.contains(DRAIN_EXCLUSIVE_ACTIONS)) 2 else 3,
      // There are 2 attempts to produce a rendering for Conflate & DEA (initial and then the
      // update.) And otherwise there are *3* attempts to produce a new rendering.
      expectedRenderingsProduced =
      if (runtime.runtimeConfig.contains(CONFLATE_STALE_RENDERINGS) ||
        runtime.runtimeConfig.contains(DRAIN_EXCLUSIVE_ACTIONS)
      ) {
        2
      } else {
        3
      },
      // Regardless only ever 2 renderings are consumed as the compose dispatcher drains all of
      // the coroutines to update state before the collector can consume a rendering.
      expectedRenderingsConsumed = 2
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
