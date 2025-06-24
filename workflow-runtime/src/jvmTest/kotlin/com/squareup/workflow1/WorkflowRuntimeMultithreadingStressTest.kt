package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions.COMPOSE_RUNTIME_ONLY
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Before
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(WorkflowExperimentalRuntime::class, ExperimentalCoroutinesApi::class)
// @Burst
class WorkflowRuntimeMultithreadingStressTest(
) {
  private val runtime: RuntimeOptions = COMPOSE_RUNTIME_ONLY

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @OptIn(DelicateCoroutinesApi::class)
  @Test
  fun actionContention() = runTest {
    // At least 2 threads so that workflow runtime can always run in parallel with at least one
    // emitter.
    val testThreadCount = calculateSaturatingTestThreadCount(minThreads = 5)

    // Determines how many separate channels are in the system.
    val childCount = (testThreadCount / 4).coerceAtLeast(2)
    // Determines how many channel sends can be queued up simultaneously.
    val emittersPerChild = (testThreadCount / 4).coerceAtLeast(2)
    // Determines how many times each emitter will loop sending actions.
    val emissionsPerEmitter = (testThreadCount * 10).coerceAtLeast(10)
    val totalEmissions = childCount * emittersPerChild * emissionsPerEmitter

    val emittersReadyLatch = CountDownLatch(childCount)
    val startEmittingLatch = Job()

    // Child launches a bunch of coroutines that loop sending outputs to the parent. We use multiple
    // emitters for each child to create contention on each channel, and loop within each coroutine
    // to prolong that contention over time as the runtime grinds through all the actions.
    // The parent renders a bunch of these children and increments a counter every time any of them
    // emit an output. We use multiple children to create contention on the select with multiple
    // channels.
    val child = Workflow.stateless {
      runningSideEffect("emitter") {
        repeat(emittersPerChild) { emitterIndex ->
          launch(start = UNDISPATCHED) {
            val action = action<Int, Nothing, Unit>("emit-$emitterIndex") { setOutput(Unit) }
            startEmittingLatch.join()
            repeat(emissionsPerEmitter) {
              actionSink.send(action)
              yield()
            }
          }
        }
        emittersReadyLatch.countDown()
      }
    }
    val root = Workflow.stateful(
      initialState = { _, _ -> 0 },
      snapshot = { null },
      render = { _, count ->
        val action = action<Unit, Int, Nothing>("countChild") { this.state++ }
        repeat(childCount) { childIndex ->
          renderChild(child, props = childIndex, key = "child-$childIndex", handler = { action })
        }
        return@stateful count
      })

    println("Thread count: $testThreadCount")
    println("Child count: $childCount")
    println("Emitters per child: $emittersPerChild")
    println("Emissions per emitter: $emissionsPerEmitter")
    println("Waiting for $totalEmissions emissions…")

    val testDispatcher = newFixedThreadPoolContext(nThreads = testThreadCount, name = "test")
    testDispatcher.use {
      val renderings = renderWorkflowIn(
        workflow = root,
        scope = backgroundScope + testDispatcher,
        props = MutableStateFlow(Unit),
        runtimeConfig = runtime.runtimeConfig,
        onOutput = {}
      )

      // Wait for all workers to spin up.
      emittersReadyLatch.awaitUntilDone()

      // Trigger an avalanche of emissions.
      startEmittingLatch.complete()

      // Wait for all workers to finish.
      val finalRendering = renderings.first { it.rendering >= totalEmissions }
      assertEquals(totalEmissions, finalRendering.rendering)
    }
  }
}
