package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.WorkflowInterceptor.RenderPassesComplete
import com.squareup.workflow1.WorkflowInterceptor.RuntimeLoopOutcome
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(WorkflowExperimentalRuntime::class, ExperimentalCoroutinesApi::class)
class AndroidRenderWorkflowInTest {

  // @Ignore("#1311: Does not yet work with immediate dispatcher.")
  @Test
  fun with_conflate_we_conflate_stacked_actions_into_one_rendering() =
    runTest(UnconfinedTestDispatcher()) {

      var childHandlerActionExecuted = false
      val trigger1 = Channel<String>(capacity = 1)
      val trigger2 = Channel<String>(capacity = 1)
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
        initialState = "unchanging state",
        render = { renderState ->
          runningWorker(
            trigger1.receiveAsFlow().asWorker()
          ) {
            action("") {
              state = it
              setOutput(it)
            }
          }
          renderState
        }
      )
      val workflow = Workflow.stateful<String, String, String>(
        initialState = "unchanging state",
        render = { renderState ->
          renderChild(childWorkflow) { childOutput ->
            action("childHandler") {
              childHandlerActionExecuted = true
              state = childOutput
            }
          }
          runningWorker(
            trigger2.receiveAsFlow().asWorker()
          ) {
            action("") {
              // Update the rendering in order to show conflation.
              state = "$it+update"
              setOutput(state)
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
          Dispatchers.Main.immediate,
        props = props,
        runtimeConfig = setOf(CONFLATE_STALE_RENDERINGS),
        workflowTracer = null,
        interceptors = listOf(countInterceptor)
      ) { }

      val renderedMutex = Mutex(locked = true)

      val collectionJob = launch(context = Dispatchers.Main.immediate, start = UNDISPATCHED) {
        // Collect this unconfined so we can get all the renderings faster than actions can
        // be processed.
        renderings.collect {
          emitted += it.rendering
          if (it.rendering == "changed state 2+update") {
            renderedMutex.unlock()
          }
        }
      }

      launch(context = Dispatchers.Main.immediate, start = UNDISPATCHED) {
        trigger1.trySend("changed state 1")
        trigger2.trySend("changed state 2")
      }.join()

      renderedMutex.lock()

      collectionJob.cancel()

      // 2 renderings (initial and then the update.) Not *3* renderings.
      assertEquals(2, emitted.size, "Expected only 2 renderings when conflating actions.")
      assertEquals(
        2,
        renderingsPassed,
        "Expected only 2 renderings passed when conflating actions."
      )
      assertEquals("changed state 2+update", emitted.last())
      assertTrue(childHandlerActionExecuted)
    }
}
