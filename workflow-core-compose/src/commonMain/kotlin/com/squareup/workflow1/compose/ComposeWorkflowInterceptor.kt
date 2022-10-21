package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.InterceptedRenderContext
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.compose.ComposeWorkflowInterceptor.ComposeRenderContextInterceptor
import com.squareup.workflow1.intercept
import kotlinx.coroutines.CoroutineScope

/**
 * Provides hooks into the workflow runtime when it is using the Compose optimizations.
 * It can be used to instrument or modify the behavior of workflows.
 *
 * @see [WorkflowInterceptor] for full documentation.
 */
public interface ComposeWorkflowInterceptor : WorkflowInterceptor {

  @Composable
  public fun <P, S, O, R> Rendering(
    renderProps: P,
    renderState: S,
    context: BaseComposeRenderContext<P, S, O>,
    session: WorkflowSession,
    proceed: @Composable (P, S, ComposeRenderContextInterceptor<P, S, O>?) -> R
  ): R = proceed(renderProps, renderState, null)

  /**
   * Intercepts calls to [BaseComposeRenderContext.ChildRendering], allowing the
   * interceptor to wrap or replace the [child] Workflow, its [childProps],
   * [key], and the [handler] function to be applied to the child's output.
   *
   * @see [RenderContextInterceptor]
   */
  public interface ComposeRenderContextInterceptor<P, S, O> : RenderContextInterceptor<P, S, O> {
    @Composable
    public fun <CP, CO, CR> ChildRendering(
      child: Workflow<CP, CO, CR>,
      childProps: CP,
      key: String,
      handler: (CO) -> WorkflowAction<P, S, O>,
      proceed: @Composable (
        child: Workflow<CP, CO, CR>,
        childProps: CP,
        key: String,
        handler: (CO) -> WorkflowAction<P, S, O>
      ) -> CR
    ): CR = proceed(child, childProps, key, handler)

    @Composable
    public fun RunningSideEffect(
      key: String,
      sideEffect: suspend () -> Unit,
      proceed: @Composable (key: String, sideEffect: suspend () -> Unit) -> Unit
    ) {
      proceed(key, sideEffect)
    }
  }
}

public fun WorkflowInterceptor.asComposeWorkflowInterceptor(): ComposeWorkflowInterceptor {
  val originalInterceptor = this
  if (originalInterceptor is ComposeWorkflowInterceptor) {
    return originalInterceptor
  }
  return object : ComposeWorkflowInterceptor {

    override fun onSessionStarted(
      workflowScope: CoroutineScope,
      session: WorkflowSession
    ) = originalInterceptor.onSessionStarted(workflowScope, session)

    override fun <P, S> onInitialState(
      props: P,
      snapshot: Snapshot?,
      proceed: (P, Snapshot?) -> S,
      session: WorkflowSession
    ): S = originalInterceptor.onInitialState(props, snapshot, proceed, session)

    override fun <P, S> onPropsChanged(
      old: P,
      new: P,
      state: S,
      proceed: (P, P, S) -> S,
      session: WorkflowSession
    ): S = originalInterceptor.onPropsChanged(old, new, state, proceed, session)

    override fun <P, S, O, R> onRender(
      renderProps: P,
      renderState: S,
      context: BaseRenderContext<P, S, O>,
      proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
      session: WorkflowSession
    ): R = originalInterceptor.onRender(renderProps, renderState, context, proceed, session)

    override fun <S> onSnapshotState(
      state: S,
      proceed: (S) -> Snapshot?,
      session: WorkflowSession
    ): Snapshot? = originalInterceptor.onSnapshotState(state, proceed, session)
  }
}

/** A [ComposeWorkflowInterceptor] that does not intercept anything. */
public object NoopComposeWorkflowInterceptor : ComposeWorkflowInterceptor

/**
 * Returns a [StatefulComposeWorkflow] that will intercept all calls to [workflow] via this
 * [ComposeWorkflowInterceptor].
 */
@WorkflowExperimentalRuntime
public fun <P, S, O, R> ComposeWorkflowInterceptor.intercept(
  workflow: StatefulComposeWorkflow<P, S, O, R>,
  workflowSession: WorkflowSession
): StatefulComposeWorkflow<P, S, O, R> = if (this === NoopComposeWorkflowInterceptor) {
  workflow
} else {
  (this as WorkflowInterceptor).intercept(workflow, workflowSession).asComposeWorkflow(
    RenderingImpl = { renderProps, renderState, context ->
      // Cannot annotate anonymous functions with @Composable and cannot infer type of
      // this when a lambda. So need this variable to make it explicit.
      val reifiedProceed: @Composable (P, S, ComposeRenderContextInterceptor<P, S, O>?) -> R =
        @Composable { props: P,
          state: S,
          interceptor: ComposeRenderContextInterceptor<P, S, O>? ->
          val interceptedContext = interceptor?.let { InterceptedComposeRenderContext(context, it) }
            ?: context
          val renderContext = ComposeRenderContext(interceptedContext, this)
          workflow.Rendering(
            props,
            state,
            renderContext
          )
        }
      Rendering(
        renderProps = renderProps,
        renderState = renderState,
        context = context,
        session = workflowSession,
        proceed = reifiedProceed
      )
    }
  )
}

public open class InterceptedComposeRenderContext<P, S, O>(
  private val baseRenderContext: BaseComposeRenderContext<P, S, O>,
  private val interceptor: ComposeRenderContextInterceptor<P, S, O>
) : BaseComposeRenderContext<P, S, O>, Sink<WorkflowAction<P, S, O>>,
  InterceptedRenderContext<P, S, O>(
    baseRenderContext,
    interceptor
  ) {

  @Composable
  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> ChildRendering(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<P, S, O>
  ): ChildRenderingT =
    interceptor.ChildRendering(
      child,
      props,
      key,
      handler
    ) @Composable { iChild, iProps, iKey, iHandler ->
      baseRenderContext.ChildRendering(iChild, iProps, iKey, iHandler)
    }

  @Composable
  override fun RunningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    val withScopeReceiver = remember(sideEffect) {
      suspend {
        CoroutineScope(activeCoroutineContext()).sideEffect()
      }
    }

    interceptor.RunningSideEffect(key, withScopeReceiver) { iKey, iSideEffect ->
      baseRenderContext.RunningSideEffect(iKey) {
        iSideEffect()
      }
    }
  }
}
