package com.squareup.benchmarks.performance.complex.poetry.instrumentation

import com.squareup.workflow1.Worker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * A Worker that adds in a key to its [toString] that [ActionHandlingTracingInterceptor] knows
 * how to read when observing action handling.
 */
@OptIn(FlowPreview::class)
class TraceableWorker<OutputT>(
  private val name: String,
  private val work: Flow<OutputT>
) : Worker<OutputT> {
  override fun run(): Flow<OutputT> = work
  override fun toString(): String = ActionHandlingTracingInterceptor.keyForTrace(name)

  companion object {
    /**
     * Just like [from()] in [Worker] but adding in a trace name.
     */
    public inline fun <reified OutputT> from(
      name: String,
      noinline block: suspend () -> OutputT
    ): Worker<OutputT> = block.asFlow().asTraceableWorker(name)
  }
}

/**
 * Just like the [Flow.asWorker()] extension in [Worker] but adding in a trace name.
 */
public inline fun <reified OutputT> Flow<OutputT>.asTraceableWorker(name: String): Worker<OutputT> =
  TraceableWorker("Worker-$name-Finished", this)
