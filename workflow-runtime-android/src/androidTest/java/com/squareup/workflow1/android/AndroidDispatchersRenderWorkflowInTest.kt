package com.squareup.workflow1.android

import android.view.Choreographer
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(WorkflowExperimentalRuntime::class, ExperimentalCoroutinesApi::class)
class AndroidDispatchersRenderWorkflowInTest {

  @Test
  fun conflate_renderings_for_multiple_worker_actions_same_trigger() =
    runTest(UnconfinedTestDispatcher()) {

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
      // Render this on Compose's AndroidUiDispatcher.Main
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope +
          AndroidUiDispatcher.Main,
        props = props,
        runtimeConfig = setOf(CONFLATE_STALE_RENDERINGS),
        workflowTracer = null,
        interceptors = listOf(countInterceptor)
      ) { }

      val renderedMutex = Mutex(locked = true)

      val collectionJob = launch {
        // Collect this unconfined so we can get all the renderings faster than actions can
        // be processed.
        renderings.collect {
          emitted += it
          println("SAE: $it")
          if (it == "state change+u1+u2+u3+u4") {
            renderedMutex.unlock()
          }
        }
      }

      trigger.value = "state change"

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

  @Test
  fun conflate_renderings_for_multiple_side_effect_actions() =
    runTest(UnconfinedTestDispatcher()) {

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
      // Render this on the Main.immediate dispatcher from Android.
      val renderings = renderWorkflowIn(
        workflow = workflow,
        scope = backgroundScope +
          AndroidUiDispatcher.Main,
        props = props,
        runtimeConfig = setOf(CONFLATE_STALE_RENDERINGS),
        workflowTracer = null,
        interceptors = listOf(countInterceptor)
      ) { }

      val renderedMutex = Mutex(locked = true)

      val collectionJob = launch {
        // Collect this unconfined so we can get all the renderings faster than actions can
        // be processed.
        renderings.collect {
          emitted += it
          if (it == "state change+u1+u2") {
            renderedMutex.unlock()
          }
        }
      }

      trigger.value = "state change"

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

  private val runtimes = setOf<RuntimeConfig>(
    RuntimeConfigOptions.RENDER_PER_ACTION,
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES),
    setOf(CONFLATE_STALE_RENDERINGS),
    setOf(STABLE_EVENT_HANDLERS),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES),
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES, STABLE_EVENT_HANDLERS),
    setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING, STABLE_EVENT_HANDLERS),
    setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING),
    setOf(
      CONFLATE_STALE_RENDERINGS,
      RENDER_ONLY_WHEN_STATE_CHANGES,
      PARTIAL_TREE_RENDERING,
      STABLE_EVENT_HANDLERS
    ),
  )

  private class SimpleScreen(
    val name: String = "Empty",
    val callback: () -> Unit,
  )

  @Test
  fun all_runtimes_handle_rendering_events_before_next_frame() {

    var mainChoreographer: Choreographer? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      mainChoreographer = Choreographer.getInstance()
    }

    var theFrameWasRun = false
    val frameCallback = Choreographer.FrameCallback {
      theFrameWasRun = true
      println("SAE: Frame callback run.")
    }

    runtimes.forEach { runtimeConfig ->
      runTest(UnconfinedTestDispatcher()) {

        println("SAE: TEST CONFIG: $runtimeConfig")

        theFrameWasRun = false
        val mutex = Mutex(locked = true)

        val workflow = Workflow.stateful<String, String, SimpleScreen>(
          initialState = "neverends",
          render = { renderState ->
            SimpleScreen(
              name = renderState,
              callback = {
                println("SAE: CALLBACK FIRED")
                actionSink.send(
                  action(
                    name = "handleInput"
                  ) {
                    println("SAE: handleInput action applied")
                    state = "$state+$state"
                  }
                )
                mainChoreographer!!.postFrameCallback(frameCallback)
                println("SAE: set up frame callback")
              }
            )
          }
        )

        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope +
            AndroidUiDispatcher.Main,
          props = MutableStateFlow(Unit).asStateFlow(),
          runtimeConfig = runtimeConfig,
          workflowTracer = null,
          interceptors = emptyList()
        ) {}

        val collectionJob = launch {
          renderings.collect {
            println("SAE: got rendering: ${it.name}")
            if (it.name == "neverends+neverends") {
              // The rendering we were looking for after the event!
              assertFalse(theFrameWasRun, "The callback on this frame was run before we" +
                "got our rendering!")
              mainChoreographer!!.removeFrameCallback(frameCallback)
              mutex.unlock()
            } else {
              it.callback()
            }
          }
        }

        mutex.lock()
        collectionJob.cancel()
      }
    }
  }

  @Test
  fun all_runtimes_handle_actions_before_the_next_frame() {
    var mainChoreographer: Choreographer? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      mainChoreographer = Choreographer.getInstance()
    }

    var theFrameWasRun = false
    val frameCallback = Choreographer.FrameCallback {
      theFrameWasRun = true
      println("SAE: Frame callback run.")
    }

    runtimes.forEach { runtimeConfig ->
      runTest(UnconfinedTestDispatcher()) {

        println("SAE: Running test with config: $runtimeConfig")

        val trigger = MutableStateFlow("unchanged state")
        val mutex = Mutex(locked = true)
        theFrameWasRun = false

        val workflow = Workflow.stateful<String, String, String>(
          initialState = "unchanged state",
          render = { renderState ->
            runningSideEffect("only1") {
              trigger.drop(1).collect {
                println("SAE: Enqueued handler message")
                actionSink.send(
                  action(
                    name = "triggerCollect",
                  ) {
                    println("SAE: Trigger handler action")
                    state = it
                  }
                )
                mainChoreographer!!.postFrameCallback(frameCallback)
              }
            }
            renderState
          }
        )

        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope +
            AndroidUiDispatcher.Main,
          props = MutableStateFlow(Unit).asStateFlow(),
          runtimeConfig = runtimeConfig,
          workflowTracer = null,
          interceptors = emptyList()
        ) {}

        val collectionJob = launch {
          // Collect this unconfined so we can get all the renderings faster than actions can
          // be processed.
          renderings.collect {
            println("SAE: Got rendering: $it, theFrameWasRun? $theFrameWasRun")
            if (it == "changed state") {
              // The rendering we were looking for!
              assertFalse(theFrameWasRun, "The callback on this frame was run before we" +
                "got our rendering!")
              mainChoreographer!!.removeFrameCallback(frameCallback)
              mutex.unlock()
            }
          }
        }

        launch {
          trigger.value = "changed state"
        }

        mutex.lock()
        collectionJob.cancel()
      }
    }
  }
}
