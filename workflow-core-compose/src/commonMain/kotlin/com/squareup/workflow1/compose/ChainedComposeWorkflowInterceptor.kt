package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.squareup.workflow1.ChainedWorkflowInterceptor
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.compose.ComposeWorkflowInterceptor.ComposeRenderContextInterceptor

internal fun List<ComposeWorkflowInterceptor>.chained(): ComposeWorkflowInterceptor =
  when {
    isEmpty() -> NoopComposeWorkflowInterceptor
    size == 1 -> single()
    else -> ChainedComposeWorkflowInterceptor(this)
  }

public class ChainedComposeWorkflowInterceptor(
  override val interceptors: List<ComposeWorkflowInterceptor>
) : ChainedWorkflowInterceptor(interceptors), ComposeWorkflowInterceptor {

  @Composable
  public override fun <P, S, O, R> Rendering(
    renderProps: P,
    renderState: S,
    context: BaseComposeRenderContext<P, S, O>,
    session: WorkflowSession,
    proceed: @Composable (P, S, ComposeRenderContextInterceptor<P, S, O>?) -> R
  ): R {
    val chainedProceed = remember(session) {
      interceptors.foldRight(proceed) { workflowInterceptor, proceedAcc ->
        { props, state, outerContextInterceptor ->
          // Holding compiler's hand for function type.
          val proceedInternal =
            remember<@Composable (P, S, ComposeRenderContextInterceptor<P, S, O>?) -> R>(
              outerContextInterceptor
            ) {
              @Composable { p: P,
                s: S,
                innerContextInterceptor: ComposeRenderContextInterceptor<P, S, O>? ->
                val contextInterceptor = remember(innerContextInterceptor) {
                  outerContextInterceptor.wrap(innerContextInterceptor)
                }
                proceedAcc(p, s, contextInterceptor)
              }
            }
          workflowInterceptor.Rendering(
            props,
            state,
            context,
            proceed = proceedInternal,
            session = session,
          )
        }
      }
    }
    return chainedProceed(renderProps, renderState, null)
  }

  public fun <P, S, O> ComposeRenderContextInterceptor<P, S, O>?.wrap(
    inner: ComposeRenderContextInterceptor<P, S, O>?
  ): ComposeRenderContextInterceptor<P, S, O>? = when {
    this == null && inner == null -> null
    this == null -> inner
    inner == null -> this
    else -> {
      // Share the base implementation.
      val regularRenderContextInterceptor = (this as RenderContextInterceptor<P, S, O>).wrap(inner)
      object : ComposeRenderContextInterceptor<P, S, O> {
        // If we don't use !!, the compiler complains about the non-elvis dot accesses below.
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        val outer = this@wrap!!

        override fun onActionSent(
          action: WorkflowAction<P, S, O>,
          proceed: (WorkflowAction<P, S, O>) -> Unit
        ) = regularRenderContextInterceptor!!.onActionSent(action, proceed)

        override fun <CP, CO, CR> onRenderChild(
          child: Workflow<CP, CO, CR>,
          childProps: CP,
          key: String,
          handler: (CO) -> WorkflowAction<P, S, O>,
          proceed: (
            child: Workflow<CP, CO, CR>,
            props: CP,
            key: String,
            handler: (CO) -> WorkflowAction<P, S, O>
          ) -> CR
        ): CR =
          regularRenderContextInterceptor!!.onRenderChild(child, childProps, key, handler, proceed)

        @Composable
        override fun <CP, CO, CR> ChildRendering(
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
        ): CR =
          outer.ChildRendering(
            child,
            childProps,
            key,
            handler
          ) @Composable { c, p, k, h ->
            inner.ChildRendering(c, p, k, h, proceed)
          }

        override fun onRunningSideEffect(
          key: String,
          sideEffect: suspend () -> Unit,
          proceed: (key: String, sideEffect: suspend () -> Unit) -> Unit
        ) = regularRenderContextInterceptor!!.onRunningSideEffect(key, sideEffect, proceed)
      }
    }
  }
}
