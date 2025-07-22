package com.squareup.workflow1.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import junit.framework.TestCase.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

class FooTest {

  private suspend fun Lifecycle.runOnceOnLifecycle(
    startWorkEvent: Event,
    cancelWorkEvent: Event,
    block: suspend CoroutineScope.() -> Unit
  ) {
    try {
      coroutineScope {
        val lifecyclePastStartEvent = Job(parent = coroutineContext.job)
        val observer = LifecycleEventObserver { _, event ->
          if (event == startWorkEvent) {
            lifecyclePastStartEvent.complete()
          }
          if (event == cancelWorkEvent || event == Event.ON_DESTROY) {
            cancel()
          }
        }
        try {
          // Add observer in the try/finally in case the job is cancelled while dispatching out
          // of the withContext.
          withContext(Dispatchers.Main.immediate) {
            addObserver(observer)
          }
          lifecyclePastStartEvent.join()
          block()
        } finally {
          withContext(NonCancellable + Dispatchers.Main.immediate) {
            removeObserver(observer)
          }
        }
      }
    } catch (e: CancellationException) {
      // This might just mean the child job was cancelled. If the SupervisorJob was cancelled, it
      // will be handled by ensureActive().
    }
    currentCoroutineContext().ensureActive()
  }

  suspend fun Lifecycle.repeatOnLifecycle(
    state: State,
    block: suspend CoroutineScope.() -> Unit
  ) {
    require(state !== INITIALIZED) {
      "repeatOnLifecycle cannot start work with the INITIALIZED lifecycle state."
    }

    val startWorkEvent = Event.upTo(state)!!
    val cancelWorkEvent = Event.downFrom(state)!!

    while (currentState !== DESTROYED) {
      runOnceOnLifecycle(startWorkEvent, cancelWorkEvent, block)
    }
  }

  private fun <T> Flow<T>.flowOnLifecycle(lifecycle: Lifecycle): Flow<T> = flow {
    lifecycle.repeatOnLifecycle(RESUMED) {
      collect { emit(it) }
    }
  }

  @Test fun foo() = runTest {
    withContext(Dispatchers.Main) {
      val lifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
      }
      val sourceFlow = flow {
        var counter = 0
        while (true) {
          emit("ping ${counter++}")
          sleep(300)
        }
      }

      backgroundScope.launch {
        sourceFlow
          .flowOnLifecycle(lifecycleOwner.lifecycle)
          .collect { println("OMG $it") }
        println("OMG collection finished")
      }

      sleep(1_000)

      lifecycleOwner.registry.setState(RESUMED)

      sleep(1_000)

      lifecycleOwner.registry.setState(CREATED)

      sleep(1_000)

      lifecycleOwner.registry.setState(RESUMED)

      sleep(1_000)

      lifecycleOwner.registry.setState(DESTROYED)

      sleep(1_000)

      fail()
    }
  }

  private suspend fun LifecycleRegistry.setState(state: State) {
    println("OMG setting state to $state")
    withContext(Dispatchers.Main) {
      currentState = state
    }
  }

  private suspend fun sleep(durationMillis: Int) {
    withContext(Dispatchers.IO) {
      Thread.sleep(durationMillis.toLong())
    }
  }
}
