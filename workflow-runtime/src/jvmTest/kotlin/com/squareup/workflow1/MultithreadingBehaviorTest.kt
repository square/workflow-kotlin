package com.squareup.workflow1

import androidx.compose.runtime.snapshots.Snapshot as ComposeSnapshot
import app.cash.burst.Burst
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions.NONE
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Deterministic tests for how the runtime behaves when it's driven from multiple threads. Tests
 * that explicitly exercise parallelism belong here; for randomized contention, see
 * [WorkflowRuntimeMultithreadingStressTest].
 */
@OptIn(ExperimentalCoroutinesApi::class, WorkflowExperimentalRuntime::class)
@Burst
class MultithreadingBehaviorTest(private val runtime: RuntimeOptions = NONE) {

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /**
   * [advanceUntilIdle] stops once no foreground tasks remain, so also run whatever the runtime
   * scheduled in [TestScope.backgroundScope].
   */
  private fun TestScope.settle() {
    advanceUntilIdle()
    runCurrent()
  }

  /**
   * Deterministically interleaves a render pass that handles new props with an action sent to the
   * same workflow from another thread. In the compose runtime the render pass runs inside the
   * Recomposer's mutable snapshot, so its props write must not conflict with the action's state
   * write, which happens outside that snapshot.
   */
  @Test
  fun action_sent_from_another_thread_during_render_pass_is_not_lost() = runTest {
    val senderThreadName = "concurrent-action-sender"
    // Counted down once the sender has written its new state, or once it has finished sending if
    // the runtime doesn't write through a snapshot state object. Only waiting for the write lets
    // the sender block on a lock held by the render pass without deadlocking the test.
    val actionWritten = CountDownLatch(1)
    val writeObserverHandle = ComposeSnapshot.registerGlobalWriteObserver {
      if (Thread.currentThread().name == senderThreadName) actionWritten.countDown()
    }
    var senderThread: Thread? = null

    val workflow =
      Workflow.stateful<Int, Int, Nothing, String>(
        initialState = { 0 },
        render = { props, state ->
          if (props == 1 && senderThread == null) {
            val sink = actionSink
            senderThread =
              thread(name = senderThreadName) {
                sink.send(action("increment") { this.state += 1 })
                actionWritten.countDown()
              }
            assertTrue(actionWritten.await(5, SECONDS), "Timed out waiting for action to be sent.")
          }
          "props=$props state=$state"
        },
      )
    val props = MutableStateFlow(0)

    try {
      val renderings =
        renderWorkflowIn(
          workflow = workflow,
          scope = backgroundScope,
          props = props,
          runtimeConfig = runtime.runtimeConfig,
        ) {}
      settle()
      assertEquals("props=0 state=0", renderings.value.rendering)

      props.value = 1
      settle()
      senderThread!!.join(SECONDS.toMillis(5))
      settle()

      assertEquals("props=1 state=1", renderings.value.rendering)
    } finally {
      writeObserverHandle.dispose()
      senderThread?.join(SECONDS.toMillis(5))
    }
  }
}
