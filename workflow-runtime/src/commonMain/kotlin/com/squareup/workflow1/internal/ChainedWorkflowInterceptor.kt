package com.squareup.workflow1.internal

import androidx.compose.runtime.Composable
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import kotlinx.coroutines.CoroutineScope

internal fun List<WorkflowInterceptor>.chained(): WorkflowInterceptor =
  when {
    isEmpty() -> NoopWorkflowInterceptor
    size == 1 -> single()
    else -> ChainedWorkflowInterceptor(this)
  }

internal class ChainedWorkflowInterceptor(
  private val interceptors: List<WorkflowInterceptor>
) : WorkflowInterceptor {

  override fun onSessionStarted(
    workflowScope: CoroutineScope,
    session: WorkflowSession
  ) {
    interceptors.forEach { it.onSessionStarted(workflowScope, session) }
  }

  override fun <P, S> onInitialState(
    props: P,
    snapshot: Snapshot?,
    proceed: (P, Snapshot?) -> S,
    session: WorkflowSession
  ): S {
    val chainedProceed = interceptors.foldRight(proceed) { workflowInterceptor, proceedAcc ->
      { props, snapshot ->
        workflowInterceptor.onInitialState(props, snapshot, proceedAcc, session)
      }
    }
    return chainedProceed(props, snapshot)
  }

  override fun <P, S> onPropsChanged(
    old: P,
    new: P,
    state: S,
    proceed: (P, P, S) -> S,
    session: WorkflowSession
  ): S {
    val chainedProceed = interceptors.foldRight(proceed) { workflowInterceptor, proceedAcc ->
      { old, new, state ->
        workflowInterceptor.onPropsChanged(old, new, state, proceedAcc, session)
      }
    }
    return chainedProceed(old, new, state)
  }

  override fun <P, S, O, R> onRender(
    renderProps: P,
    renderState: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
    session: WorkflowSession
  ): R {
    val chainedProceed = interceptors.foldRight(proceed) { workflowInterceptor, proceedAcc ->
      { props, state, outerContextInterceptor ->
        workflowInterceptor.onRender(
          props,
          state,
          context,
          proceed = { p, s, innerContextInterceptor: RenderContextInterceptor<P, S, O>? ->
            val contextInterceptor = outerContextInterceptor.wrap(innerContextInterceptor)
            proceedAcc(p, s, contextInterceptor)
          },
          session = session,
        )
      }
    }
    return chainedProceed(renderProps, renderState, null)
  }

  @Composable
  override fun <P, S, O, R> Rendering(
    renderProps: P,
    renderState: S,
    hoistRendering: @Composable (R) -> Unit,
    context: BaseRenderContext<P, S, O>,
    session: WorkflowSession,
    proceed: @Composable (P, S, RenderContextInterceptor<P, S, O>?, @Composable (R) -> Unit) -> Unit
  ) {
    val chainedProceed = interceptors.foldRight(proceed) { workflowInterceptor, proceedAcc ->
      { props, state, outerContextInterceptor, outerHoistRendering ->
        // Holding compiler's hand for function type.
        val proceedInternal =
          @Composable { p: P,
            s: S,
            innerContextInterceptor: RenderContextInterceptor<P, S, O>?,
            innerHoistRendering: @Composable (R) -> Unit ->
            val contextInterceptor = outerContextInterceptor.wrap(innerContextInterceptor)
            proceedAcc(p, s, contextInterceptor, innerHoistRendering)
          }
        workflowInterceptor.Rendering(
          props,
          state,
          outerHoistRendering,
          context,
          proceed = proceedInternal,
          session = session,
        )
      }
    }
    chainedProceed(renderProps, renderState, null, hoistRendering)
  }

  override fun <S> onSnapshotState(
    state: S,
    proceed: (S) -> Snapshot?,
    session: WorkflowSession
  ): Snapshot? {
    val chainedProceed = interceptors.foldRight(proceed) { workflowInterceptor, proceedAcc ->
      { state ->
        workflowInterceptor.onSnapshotState(state, proceedAcc, session)
      }
    }
    return chainedProceed(state)
  }

  private fun <P, S, O> RenderContextInterceptor<P, S, O>?.wrap(
    inner: RenderContextInterceptor<P, S, O>?
  ) = when {
    this == null && inner == null -> null
    this == null -> inner
    inner == null -> this
    else -> object : RenderContextInterceptor<P, S, O> {
      // If we don't use !!, the compiler complains about the non-elvis dot accesses below.
      @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
      val outer = this@wrap!!

      override fun onActionSent(
        action: WorkflowAction<P, S, O>,
        proceed: (WorkflowAction<P, S, O>) -> Unit
      ) {
        outer.onActionSent(action) { interceptedAction ->
          inner.onActionSent(interceptedAction, proceed)
        }
      }

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
      ): CR = outer.onRenderChild(child, childProps, key, handler) { c, p, k, h ->
        inner.onRenderChild(c, p, k, h, proceed)
      }

      @Composable
      override fun <CP, CO, CR> ChildRendering(
        child: Workflow<CP, CO, CR>,
        childProps: CP,
        key: String,
        hoistRendering: @Composable (CR) -> Unit,
        handler: (CO) -> WorkflowAction<P, S, O>,
        proceed: @Composable (
          child: Workflow<CP, CO, CR>,
          childProps: CP,
          key: String,
          onRender: @Composable (CR) -> Unit,
          handler: (CO) -> WorkflowAction<P, S, O>
        ) -> Unit
      ): Unit =
        outer.ChildRendering(
          child,
          childProps,
          key,
          hoistRendering,
          handler
        ) @Composable { c, p, k, o, h ->
          inner.ChildRendering(c, p, k, o, h, proceed)
        }

      override fun onRunningSideEffect(
        key: String,
        sideEffect: suspend () -> Unit,
        proceed: (key: String, sideEffect: suspend () -> Unit) -> Unit
      ) {
        outer.onRunningSideEffect(key, sideEffect) { iKey, iSideEffect ->
          inner.onRunningSideEffect(iKey, iSideEffect, proceed)
        }
      }
    }
  }
}
