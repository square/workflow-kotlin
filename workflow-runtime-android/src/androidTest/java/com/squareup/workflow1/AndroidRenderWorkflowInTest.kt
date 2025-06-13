package com.squareup.workflow1

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.PARTIAL_TREE_RENDERING
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
import com.squareup.workflow1.RuntimeConfigOptions.STABLE_EVENT_HANDLERS
import com.squareup.workflow1.WorkflowInterceptor.RenderPassesComplete
import com.squareup.workflow1.WorkflowInterceptor.RuntimeLoopOutcome
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(WorkflowExperimentalRuntime::class, ExperimentalCoroutinesApi::class)
class AndroidRenderWorkflowInTest {

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

      val collectionJob = launch(context = Dispatchers.Main.immediate) {
        // Collect this unconfined so we can get all the renderings faster than actions can
        // be processed.
        renderings.collect {
          emitted += it.rendering
          println("SAE: ${it.rendering}")
          if (it.rendering == "state change+u1+u2+u3+u4") {
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
  fun conflate_renderings_for_multiple_side_effect_actions_when_deferrable() =
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
                  isDeferrable = true,
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
                  isDeferrable = true
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
          Dispatchers.Main.immediate,
        props = props,
        runtimeConfig = setOf(CONFLATE_STALE_RENDERINGS),
        workflowTracer = null,
        interceptors = listOf(countInterceptor)
      ) { }

      val renderedMutex = Mutex(locked = true)

      val collectionJob = launch(context = Dispatchers.Main.immediate) {
        // Collect this unconfined so we can get all the renderings faster than actions can
        // be processed.
        renderings.collect {
          emitted += it.rendering
          if (it.rendering == "state change+u1+u2") {
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

  @Test
  fun do_not_conflate_renderings_for_multiple_side_effect_actions_when_NOT_deferrable() =
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
          Dispatchers.Main.immediate,
        props = props,
        runtimeConfig = setOf(CONFLATE_STALE_RENDERINGS),
        workflowTracer = null,
        interceptors = listOf(countInterceptor)
      ) { }

      val renderedMutex = Mutex(locked = true)

      val collectionJob = launch(context = Dispatchers.Main.immediate) {
        // Collect this unconfined so we can get all the renderings faster than actions can
        // be processed.
        renderings.collect {
          emitted += it.rendering
          if (it.rendering == "state change+u1+u2") {
            renderedMutex.unlock()
          }
        }
      }

      trigger.value = "state change"

      renderedMutex.lock()

      collectionJob.cancel()

      // 3 renderings! each update separate.
      assertEquals(3, emitted.size, "Expected 3 emitted renderings when conflating actions.")
      assertEquals(
        3,
        renderingsPassed,
        "Expected 3 renderings passed to interceptor when conflating actions."
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
  fun all_runtimes_handle_rendering_events_in_one_message_from_callback() {
    // Main thread handler.
    val handler = Handler(Looper.getMainLooper())

    runtimes.forEach { runtimeConfig ->
      runTest(UnconfinedTestDispatcher()) {

        var nextMessageRan = false
        val theNextMessage = Message.obtain(handler) {
          nextMessageRan = true
        }
        val countDownLatch = CountDownLatch(1)

        val workflow = Workflow.stateful<String, String, SimpleScreen>(
          initialState = "neverends",
          render = { renderState ->
            SimpleScreen(
              name = renderState,
              callback = {
                actionSink.send(
                  action(
                    name = "handleInput"
                  ) {
                    state = "$state+$state"
                  }
                )
                // If we do not end the test within 1 main thread message we'll blow up.
                assertTrue(
                  handler.sendMessage(theNextMessage),
                  message = "Could not send to handler. This test does not work without that."
                )
              }
            )
          }
        )

        val renderings = renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope +
            Dispatchers.Main.immediate,
          props = MutableStateFlow(Unit).asStateFlow(),
          runtimeConfig = runtimeConfig,
          workflowTracer = null,
          interceptors = emptyList()
        ) {}

        val collectionJob = launch(context = Dispatchers.Main.immediate) {
          // Collect this unconfined so we can get all the renderings faster than actions can
          // be processed.
          renderings.collect {
            if (it.rendering.name == "neverends+neverends") {
              // The rendering we were looking for!
              assertFalse(nextMessageRan, "The sent message ran :(.")
              countDownLatch.countDown()
            } else {
              it.rendering.callback()
            }
          }
        }

        countDownLatch.await()
        collectionJob.cancel()
      }
    }
  }

  @Test
  fun all_runtimes_handle_deferrable_actions_in_one_message_from_action_applied() {
    // Main thread handler.
    val handler = Handler(Looper.getMainLooper())

    runtimes.forEach { runtimeConfig ->
      runTest(UnconfinedTestDispatcher()) {

        val trigger = MutableStateFlow("unchanged state")

        var nextMessageRan = false
        val theNextMessage = Message.obtain(handler) {
          nextMessageRan = true
        }
        val countDownLatch = CountDownLatch(1)

        val workflow = Workflow.stateful<String, String, String>(
          initialState = "unchanged state",
          render = { renderState ->
            runningSideEffect("only1") {
              trigger.drop(1).collect {
                actionSink.send(
                  action(
                    name = "triggerCollect",
                    isDeferrable = true
                  ) {
                    state = it
                    // If we do not end the test within 1 main thread message we'll blow up.
                    assertTrue(
                      handler.sendMessage(theNextMessage),
                      message = "Could not send to handler. This test does not work without that."
                    )
                  }
                )
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
          runtimeConfig = runtimeConfig,
          workflowTracer = null,
          interceptors = emptyList()
        ) {}

        val collectionJob = launch(context = Dispatchers.Main.immediate) {
          // Collect this unconfined so we can get all the renderings faster than actions can
          // be processed.
          renderings.collect {
            if (it.rendering == "changed state") {
              // The rendering we were looking for!
              assertFalse(nextMessageRan, "The sent message ran :(.")
              countDownLatch.countDown()
            }
          }
        }

        trigger.emit("changed state")

        countDownLatch.await()
        collectionJob.cancel()
      }
    }
  }
}
