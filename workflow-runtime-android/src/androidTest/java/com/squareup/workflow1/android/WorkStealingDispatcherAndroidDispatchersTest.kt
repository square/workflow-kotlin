package com.squareup.workflow1.android

import com.squareup.workflow1.internal.WorkStealingDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Test
import kotlin.test.assertEquals

class WorkStealingDispatcherAndroidDispatchersTest {

  @Test fun dispatch_runsImmediatelyWhenDelegateIsMainImmediate_onMainThread() = runTest {
    val dispatcher = WorkStealingDispatcher(Dispatchers.Main.immediate)

    runOnMainThread {
      expect(0)
      dispatcher.dispatch {
        expect(1)
      }
      expect(2)
    }
  }

  @Test fun dispatchNested_enqueuesWhenDelegateIsMainImmediate_onMainThread() = runTest {
    val dispatcher = WorkStealingDispatcher(Dispatchers.Main.immediate)

    runOnMainThread {
      expect(0)
      dispatcher.dispatch {
        expect(1)

        // This dispatch should get enqueued to Unconfined's threadlocal queue.
        dispatcher.dispatch {
          expect(3)
        }

        expect(2)
      }
      expect(4)
    }
  }

  @Test fun dispatch_queues_whenDelegateisMain_onMainThread() = runTest {
    val dispatcher = WorkStealingDispatcher(Dispatchers.Main)

    runOnMainThread {
      expect(0)
      dispatcher.dispatch {
        expect(2)
      }
      expect(1)

      yield()
      expect(3)
    }
  }

  @Test fun dispatch_runsMultipleTasksInOrder_whenDelegateIsMain_onMainThread() = runTest {
    val dispatcher = WorkStealingDispatcher(Dispatchers.Main)

    runOnMainThread {
      expect(0)
      dispatcher.dispatch {
        expect(3)
      }
      expect(1)
      dispatcher.dispatch {
        expect(4)
      }
      expect(2)

      yield()
      expect(5)
    }
  }

  private suspend fun runOnMainThread(block: suspend CoroutineScope.() -> Unit) {
    withContext(Dispatchers.Main, block)
  }

  private fun CoroutineDispatcher.dispatch(block: () -> Unit) {
    dispatch(this) { block() }
  }

  private val expectLock = Any()
  private var current = 0
  private fun expect(expected: Int) {
    synchronized(expectLock) {
      assertEquals(expected, current, "Expected to be at step $expected but was at $current")
      current++
    }
  }
}
