package com.squareup.workflow1.cotracer

import com.squareup.workflow1.cotracer.TracingContinuationInterceptorWrapper.Companion.wrapInterceptorWithTracing
import com.squareup.workflow1.cotracer.TracingCoroutineContext.Companion.wrapContextWithTracing
import com.squareup.workflow1.cotracer.TracingCoroutineDispatcher.Companion.wrapDispatcherWithTracing
import com.squareup.workflow1.internal.Lock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

public suspend fun <R> withCoTracing(
  block: suspend (CoTracer) -> R
): R {
  val context = coroutineContext
  var tracingElement = context[CoTracerContextElement]
  if (tracingElement != null) {
    return block(tracingElement.tracer)
  }

  val tracer = CoTracer()
  tracingElement = CoTracerContextElement(tracer)
  val wrappedContext = (context + tracingElement).wrapContextWithTracing(tracer)
  return withContext(wrappedContext) {
    block(tracer)
  }
}

public class CoTracer {
  private val lock = Lock()
  private var knownJobs = mutableMapOf<Job, JobTracer>()
}

private class CoTracerContextElement(
  val tracer: CoTracer
) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<CoTracerContextElement>
}

private class TracingCoroutineContext private constructor(
  private val tracer: CoTracer,
  private val baseContext: CoroutineContext,
  private var tracingInterceptor: TracingContinuationInterceptor
) : CoroutineContext by baseContext {

  override fun plus(context: CoroutineContext): CoroutineContext {
    if (context === EmptyCoroutineContext) return this

    @Suppress("NAME_SHADOWING")
    val context = context.unwrapTracingContext()
    val otherInterceptor = context[ContinuationInterceptor]

    if (otherInterceptor === tracingInterceptor) {
      // New context does not change the interceptor, we can reuse it.
      return copyWithContext(context)
    }

    if (otherInterceptor === tracingInterceptor.baseInterceptor) {
      // New context changes the interceptor but it's the same base, don't re-wrap.
      return copyWithContext(context + tracingInterceptor)
    }

    // New context changes the interceptor.
    if (otherInterceptor is TracingContinuationInterceptor && otherInterceptor.tracer === tracer) {
      // New interceptor is from the same tracer, reuse it.
      return copyWithContext(context, otherInterceptor)
    }

    val tracingInterceptor = otherInterceptor.wrapInterceptorWithTracing(tracer)
    return copyWithContext(context + tracingInterceptor, tracingInterceptor)
  }

  private fun copyWithContext(
    context: CoroutineContext,
    tracingInterceptor: TracingContinuationInterceptor = this.tracingInterceptor,
  ) = TracingCoroutineContext(
    tracer = tracer,
    baseContext = baseContext + context,
    tracingInterceptor = tracingInterceptor
  )

  companion object {
    fun CoroutineContext.wrapContextWithTracing(tracer: CoTracer): TracingCoroutineContext {
      if (this is TracingCoroutineContext && this.tracer === tracer) {
        return this
      }

      val interceptor = this[ContinuationInterceptor].wrapInterceptorWithTracing(tracer)
      return TracingCoroutineContext(
        tracer = tracer,
        baseContext = this + interceptor,
        tracingInterceptor = interceptor
      )
    }

    private fun CoroutineContext.unwrapTracingContext(): CoroutineContext =
      if (this is TracingCoroutineContext) baseContext else this
  }
}

private interface TracingContinuationInterceptor : ContinuationInterceptor {
  val tracer: CoTracer
  val baseInterceptor: ContinuationInterceptor
}

private class TracingContinuationInterceptorWrapper private constructor(
  override val tracer: CoTracer,
  override val baseInterceptor: ContinuationInterceptor
) : TracingContinuationInterceptor, AbstractCoroutineContextElement(ContinuationInterceptor) {

  override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
    val tracingContinuation = TracingContinuation(tracer, continuation)
  }

  private class TracingContinuation<T>(
    private val tracer: CoTracer,
    private val baseContinuation: Continuation<T>
  )

  companion object {
    fun ContinuationInterceptor?.wrapInterceptorWithTracing(
      tracer: CoTracer
    ): TracingContinuationInterceptor {
      if (this == null) {
        return Dispatchers.Default.wrapInterceptorWithTracing(tracer)
      }

      // New context sets an interceptor, wrap it for tracing.
      if (this is TracingContinuationInterceptor && this.tracer === tracer) {
        // New interceptor is from the same tracer, reuse it.
        return this
      }

      // Dispatchers need to be wrapped with dispatchers.
      if (this is CoroutineDispatcher) {
        return wrapDispatcherWithTracing(tracer)
      }

      return TracingContinuationInterceptorWrapper(tracer, this)
    }
  }
}

private class TracingCoroutineDispatcher private constructor(
  override val tracer: CoTracer,
  override val baseInterceptor: CoroutineDispatcher
) : CoroutineDispatcher(), TracingContinuationInterceptor {
  override fun dispatch(
    context: CoroutineContext,
    block: Runnable
  ) {
    TODO("Not yet implemented")
  }

  companion object {
    fun CoroutineDispatcher.wrapDispatcherWithTracing(
      tracer: CoTracer
    ): TracingCoroutineDispatcher {
      TODO()
    }
  }
}

private class JobTracer {

}
