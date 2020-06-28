/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow.internal

import com.squareup.workflow.ExperimentalWorkflowApi
import com.squareup.workflow.RenderContext
import com.squareup.workflow.Snapshot
import com.squareup.workflow.WorkflowInterceptor
import com.squareup.workflow.NoopWorkflowInterceptor
import com.squareup.workflow.WorkflowInterceptor.WorkflowSession
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
    context: RenderContext<S, O>,
    proceed: (P, S, RenderContext<S, O>) -> R,
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
