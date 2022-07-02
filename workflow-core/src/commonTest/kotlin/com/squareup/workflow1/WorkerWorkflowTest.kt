package com.squareup.workflow1

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkerWorkflowTest {

  @Test fun runWorker_coroutine_is_named_without_key() {
    val worker = CoroutineNameWorker()
    runBlocking {
      runWorker(worker, renderKey = "", actionSink = NoopSink)
    }

    assertEquals("CoroutineNameWorker.toString", worker.recordedName)
  }

  @Test fun runWorker_coroutine_is_named_with_key() {
    val worker = CoroutineNameWorker()
    runBlocking {
      runWorker(worker, renderKey = "foo", actionSink = NoopSink)
    }

    assertEquals("CoroutineNameWorker.toString:foo", worker.recordedName)
  }

  private object NoopSink : Sink<Any?> {
    override fun send(value: Any?) {
      // Noop
    }
  }

  private class CoroutineNameWorker : Worker<String> {
    var recordedName: String? = null
      private set

    override fun run(): Flow<String> = flow {
      recordedName = (coroutineContext[CoroutineName] as CoroutineName).name
    }

    override fun toString(): String = "CoroutineNameWorker.toString"
  }
}
