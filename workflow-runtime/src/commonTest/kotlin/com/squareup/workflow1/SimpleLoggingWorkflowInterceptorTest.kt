package com.squareup.workflow1

import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

internal class SimpleLoggingWorkflowInterceptorTest {

  @Test fun onSessionStarted_handles_logging_exceptions() {
    val interceptor = ErrorLoggingInterceptor()
    val scope = CoroutineScope(EmptyCoroutineContext)
    interceptor.onSessionStarted(scope, TestWorkflowSession)
    scope.cancel()

    assertEquals(ErrorLoggingInterceptor.EXPECTED_ERRORS, interceptor.errors)
  }

  @Test fun onInitialState_handles_logging_exceptions() {
    val interceptor = ErrorLoggingInterceptor()
    interceptor.onInitialState(
      Unit,
      null,
      CoroutineScope(EmptyCoroutineContext),
      { _, _, _ -> },
      TestWorkflowSession
    )

    assertEquals(ErrorLoggingInterceptor.EXPECTED_ERRORS, interceptor.errors)
  }

  @Test fun onPropsChanged_handles_logging_exceptions() {
    val interceptor = ErrorLoggingInterceptor()
    interceptor.onPropsChanged(Unit, Unit, Unit, { _, _, _ -> }, TestWorkflowSession)

    assertEquals(ErrorLoggingInterceptor.EXPECTED_ERRORS, interceptor.errors)
  }

  @Test fun onRender_handles_logging_exceptions() {
    val interceptor = ErrorLoggingInterceptor()

    interceptor.onRender<Unit, Unit, Nothing, Any>(
      renderProps = Unit,
      renderState = Unit,
      context = FakeRenderContext,
      { _, _, _ -> },
      TestWorkflowSession,
    )

    assertEquals(ErrorLoggingInterceptor.EXPECTED_ERRORS, interceptor.errors)
  }

  @Test fun onSnapshotState_handles_logging_exceptions() {
    val interceptor = ErrorLoggingInterceptor()
    interceptor.onSnapshotState(Unit, { null }, TestWorkflowSession)

    assertEquals(ErrorLoggingInterceptor.EXPECTED_ERRORS, interceptor.errors)
  }

  private open class ErrorLoggingInterceptor : SimpleLoggingWorkflowInterceptor() {
    val errors = mutableListOf<String>()

    override fun log(text: String) {
      throw IllegalArgumentException()
    }

    override fun logError(text: String) {
      errors += text
    }

    companion object {
      val EXPECTED_ERRORS = listOf(
        "ErrorLoggingInterceptor.logBeforeMethod threw exception:\n" +
          ILLEGAL_ARGUMENT_EXCEPTION_NAME,
        "ErrorLoggingInterceptor.logAfterMethod threw exception:\n" +
          ILLEGAL_ARGUMENT_EXCEPTION_NAME
      )
    }
  }

  private object TestWorkflowSession : WorkflowSession {
    override val identifier: WorkflowIdentifier = unsnapshottableIdentifier(typeOf<Unit>())
    override val renderKey: String get() = "key"
    override val sessionId: Long get() = 42
    override val parent: WorkflowSession? get() = null
    override val runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG
    override val workflowTracer: WorkflowTracer? = null
  }

  private object FakeRenderContext : BaseRenderContext<Unit, Unit, Unit> {
    override val runtimeConfig: RuntimeConfig = emptySet()
    override val actionSink: Sink<WorkflowAction<Unit, Unit, Unit>>
      get() = fail()
    override val workflowTracer: WorkflowTracer? = null

    override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      handler: (ChildOutputT) -> WorkflowAction<Unit, Unit, Unit>
    ): ChildRenderingT {
      fail()
    }

    override fun runningSideEffect(
      key: String,
      sideEffect: suspend CoroutineScope.() -> Unit
    ) {
      fail()
    }

    override fun <ResultT> remember(
      key: String,
      resultType: KType,
      vararg inputs: Any?,
      calculation: () -> ResultT
    ): ResultT {
      fail()
    }
  }
}
