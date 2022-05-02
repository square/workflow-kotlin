package com.squareup.workflow1.diagnostic.tracing

import com.squareup.tracing.TimeMark
import com.squareup.tracing.TraceEncoder
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.asWorker
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.runningWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okio.Buffer
import okio.buffer
import okio.source
import org.mockito.kotlin.mock
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class TracingWorkflowInterceptorTest {

  private lateinit var onGcDetected: () -> Unit

  @Test fun `golden value`() {
    val buffer = Buffer()
    val memoryStats = object : MemoryStats {
      override fun freeMemory(): Long = 42
      override fun totalMemory(): Long = 43
    }
    val gcDetector = mock<GcDetector>()
    val scope = CoroutineScope(Unconfined)
    val encoder = TraceEncoder(
      scope = scope,
      start = ZeroTimeMark,
      ioDispatcher = Unconfined,
      sinkProvider = { buffer }
    )
    val listener = TracingWorkflowInterceptor(
      memoryStats = memoryStats,
      gcDetectorConstructor = {
        onGcDetected = it
        gcDetector
      }
    ) { workflowScope, type ->
      provideLogger("", workflowScope, type) { encoder }
    }
    val props = (0..100).asFlow()
      // Real use cases almost never feed a firehose of changing root props, they change rarely if
      // at all, and almost certainly allow processing of dispatched coroutines in between. This
      // yield represents that more accurately.
      .onEach {
        yield()
        yield()
      }

    runBlocking(scope.coroutineContext) {
      val renderings = renderWorkflowIn(
        TestWorkflow(), scope, props.stateIn(this),
        interceptors = listOf(listener),
        onOutput = {}
      ).map { it.rendering }

      renderings.takeWhile { it != "final" }
        .collect()
    }
    scope.cancel()

    val expected = TracingWorkflowInterceptorTest::class.java
      .getResourceAsStream("expected_trace_file.txt")
      .source()
      .buffer()
    assertEquals(expected.readUtf8(), buffer.readUtf8())
  }

  private inner class TestWorkflow : StatefulWorkflow<Int, String, String, String>() {

    private val channel = Channel<String>(UNLIMITED)

    override fun toString(): String =
      "TestWorkflow"

    fun triggerWorker(value: String) {
      channel.trySend(value).isSuccess
    }

    override fun initialState(
      props: Int,
      snapshot: Snapshot?
    ): String {
      // Pretend to detect a garbage collection whenever a workflow starts.
      onGcDetected()
      return "initial"
    }

    override fun onPropsChanged(
      old: Int,
      new: Int,
      state: String
    ): String {
      if (old == 2 && new == 3) triggerWorker("fired!")
      return if (old == 0 && new == 1) "changed state" else state
    }

    override fun render(
      renderProps: Int,
      renderState: String,
      context: RenderContext
    ): String {
      if (renderProps == 0) return "initial"
      if (renderProps in 1..6) context.renderChild(this, 0) { bubbleUp(it) }
      if (renderProps in 4..5) context.renderChild(this, props = 1, key = "second") { bubbleUp(it) }
      if (renderProps in 2..3) context.runningWorker(
        channel.receiveAsFlow()
          .asWorker()
      ) { bubbleUp(it) }

      return if (renderProps > 10) "final" else "rendering"
    }

    override fun snapshotState(state: String): Snapshot? = null

    private fun bubbleUp(output: String) = action { setOutput(output) }
  }
}

private object ZeroTimeMark : TimeMark {
  override val elapsedNow: Long = 0L
}
