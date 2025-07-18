package com.squareup.workflow1

import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.util.concurrent.CountDownLatch
import kotlin.test.Test

class WorkflowRuntimeMultithreadingStressTest {

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
    val child = Workflow.stateless { childIndex: Int ->
      runningSideEffect("emitter") {
        repeat(emittersPerChild) { emitterIndex ->
          launch(start = UNDISPATCHED) {
            val action = action<Int, Nothing, Unit>("emit-$emitterIndex") { setOutput(Unit) }
            startEmittingLatch.join()
            repeat(emissionsPerEmitter) { emissionIndex ->
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

    val testDispatcher = newFixedThreadPoolContext(nThreads = testThreadCount, name = "test")
    testDispatcher.use {
      val renderings = renderWorkflowIn(
        workflow = root,
        scope = backgroundScope + testDispatcher,
        props = MutableStateFlow(Unit),
        onOutput = {}
      )

      // Wait for all workers to spin up.
      emittersReadyLatch.awaitUntilDone()
      println("Thread count: $testThreadCount")
      println("Child count: $childCount")
      println("Emitters per child: $emittersPerChild")
      println("Emissions per emitter: $emissionsPerEmitter")
      println("Waiting for $totalEmissions emissions…")

      // Trigger an avalanche of emissions.
      startEmittingLatch.complete()

      // Wait for all workers to finish.
      renderings.first { it.rendering == totalEmissions }
    }
  }
}
