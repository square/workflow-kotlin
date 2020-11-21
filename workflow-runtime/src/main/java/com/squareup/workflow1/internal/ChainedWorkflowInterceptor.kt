package com.squareup.workflow1.internal

import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalWorkflowApi::class)
internal fun List<WorkflowInterceptor>.chained(): WorkflowInterceptor =
  when {
    isEmpty() -> NoopWorkflowInterceptor
    size == 1 -> single()
    else -> ChainedWorkflowInterceptor(this)
  }

@OptIn(ExperimentalWorkflowApi::class)
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
    props: P,
    state: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, BaseRenderContext<P, S, O>) -> R,
    session: WorkflowSession
  ): R {
    val chainedProceed = interceptors.foldRight(proceed) { workflowInterceptor, proceedAcc ->
      { props, state, context ->
        workflowInterceptor.onRender(props, state, context, proceedAcc, session)
      }
    }
    return chainedProceed(props, state, context)
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
}
