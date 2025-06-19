@file:OptIn(WorkflowExperimentalRuntime::class)
@file:Suppress("JUnitMalformedDeclaration")

package com.squareup.workflow1.android

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import papa.Choreographers
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

@OptIn(WorkflowExperimentalRuntime::class)
@Burst
class AndroidDispatchersRenderWorkflowInTest(
  private val runtime: RuntimeOptions = RuntimeOptions.DEFAULT
) {

  @Ignore("See https://github.com/square/workflow-kotlin/issues/1311")
  @Test
  fun conflate_renderings_for_multiple_worker_actions_same_trigger() =
    runTest {

      val trigger = MutableStateFlow("unchanged state")
      val emitted = mutableListOf<String>()
      var renderingsPassed = 0
      val countInterceptor = object : WorkflowInterceptor {
        override fun onRuntimeLoopTick(outcome: RuntimeLoopOutcome) {
          if (outcome is RenderPassesComplete<*>) {
            renderingsPassed++
          }
        }
      }

      val childWorkflow = Workflow.stateful<String, String, String>(
        initialState = "unchanged state",
        render = { renderState ->
          runningWorker(
            worker = trigger.drop(1).asWorker(),
            key = "Worker1"
          ) {
            action("") {
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
            action("childHandler") {
              state = childOutput
            }
          }
          runningWorker(
            worker = trigger.drop(1).asWorker(),
            key = "Worker2"
          ) {
            action("") {
              // Update the state in order to show conflation.
              state = "$state+u2"
            }
          }
          runningWorker(
            worker = trigger.drop(1).asWorker(),
            key = "Worker3"
          ) {
            action("") {
              // Update the state in order to show conflation.
              state = "$state+u3"
            }
          }
          runningWorker(
            worker = trigger.drop(1).asWorker(),
            key = "Worker4"
          ) {
            action("") {
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
          Dispatchers.Main.immediate,
        props = props,
        runtimeConfig = setOf(CONFLATE_STALE_RENDERINGS),
        workflowTracer = null,
        interceptors = listOf(countInterceptor)
      ) { }

      val renderedMutex = Mutex(locked = true)

      val collectionJob = launch {
        renderings.collect {
          emitted += it
          if (it == "state change+u1+u2+u3+u4") {
            renderedMutex.unlock()
          }
        }
      }

      testScheduler.advanceUntilIdle()

      launch {
        trigger.value = "state change"
      }

      testScheduler.advanceUntilIdle()

      renderedMutex.lock()

      collectionJob.cancel()

      // 2 renderings (initial and then the update.) Not *5* renderings.
      assertEquals(2, emitted.size, "Expected only 2 emitted renderings when conflating actions.")
      assertEquals(
        2,
        renderingsPassed,
        "Expected only 2 renderings passed to interceptor when conflating actions."
      )
      assertEquals("state change+u1+u2+u3+u4", emitted.last())
    }

  @Ignore("See https://github.com/square/workflow-kotlin/issues/1311")
  @Test
  fun conflate_renderings_for_multiple_side_effect_actions() =
    runTest {

      val trigger = MutableStateFlow("unchanged state")
      val emitted = mutableListOf<String>()
      var renderingsPassed = 0
      val countInterceptor = object : WorkflowInterceptor {
        override fun onRuntimeLoopTick(outcome: RuntimeLoopOutcome) {
          if (outcome is RenderPassesComplete<*>) {
            renderingsPassed++
          }
        }
      }

      val childWorkflow = Workflow.stateful<String, String, String>(
        initialState = "unchanged state",
        render = { renderState ->
          runningSideEffect("childSideEffect") {
            trigger.drop(1).collect {
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
            trigger.drop(1).collect {
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
          Dispatchers.Main.immediate,
        props = props,
        runtimeConfig = setOf(CONFLATE_STALE_RENDERINGS),
        workflowTracer = null,
        interceptors = listOf(countInterceptor)
      ) { }

      val renderedMutex = Mutex(locked = true)

      val collectionJob = launch {
        renderings.collect {
          emitted += it
          if (it == "state change+u1+u2") {
            renderedMutex.unlock()
          }
        }
      }

      testScheduler.advanceUntilIdle()

      launch {
        trigger.value = "state change"
      }

      testScheduler.advanceUntilIdle()

      renderedMutex.lock()

      collectionJob.cancel()

      // 2 renderings (initial and then the update.) Not *3* renderings.
      assertEquals(2, emitted.size, "Expected only 2 emitted renderings when conflating actions.")
      assertEquals(
        2,
        renderingsPassed,
        "Expected only 2 renderings passed to interceptor when conflating actions."
      )
      assertEquals("state change+u1+u2", emitted.last())
    }

  private class SimpleScreen(
    val name: String = "Empty",
    val callback: () -> Unit,
  )

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
  fun all_runtimes_handle_side_effect_actions_before_the_next_frame() =
    runTest {
      val completedMutex = Mutex(locked = true)
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

      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope +
          Dispatchers.Main.immediate,
        props = MutableStateFlow(Unit).asStateFlow(),
        runtimeConfig = runtime.runtimeConfig,
        workflowTracer = null,
        interceptors = emptyList()
      ) {}

      val collectionJob = launch(Dispatchers.Main.immediate) {
        renderings.collect {
          if (it == "changed state") {
            // The rendering we were looking for!
            expectInOrder(1)
            completedMutex.unlock()
          } else {
            expectInOrder(0)
            Choreographers.postOnFrameRendered {
              // We are expecting this to happen last, after we get the rendering!
              expectInOrder(2)
            }
            trigger.emit("changed state")
          }
        }
      }

      completedMutex.lock()
      collectionJob.cancel()
    }

  @Test
  fun all_runtimes_handle_rendering_events_before_next_frame() = runTest {
    val completedMutex = Mutex(locked = true)
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
        Dispatchers.Main.immediate,
      props = MutableStateFlow(Unit).asStateFlow(),
      runtimeConfig = runtime.runtimeConfig,
      workflowTracer = null,
      interceptors = emptyList()
    ) {}

    val collectionJob = launch(Dispatchers.Main.immediate) {
      renderings.collect {
        if (it.name == "neverends+neverends") {
          // The rendering we were looking for after the event!
          expectInOrder(1)
          completedMutex.unlock()
        } else {
          expectInOrder(0)
          Choreographers.postOnFrameRendered {
            // This should be happening last!
            expectInOrder(2)
          }
          // First rendering, lets call it.
          it.callback()
        }
      }
    }

    completedMutex.lock()
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
