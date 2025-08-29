package com.squareup.sample.thingy

import com.squareup.workflow1.Sink
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.ui.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

internal class BackStackScopeImpl<OutputT>(
  coroutineScope: CoroutineScope,
) : BackStackScope, CoroutineScope by coroutineScope {
  // TODO set this
  lateinit var actionSink: Sink<WorkflowAction<Any?, BackStackState, OutputT>>

  override suspend fun <ChildPropsT, ChildOutputT, R> showWorkflow(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    props: Flow<ChildPropsT>,
    onOutput: suspend BackStackWorkflowScope.(ChildOutputT) -> R
  ): R = showWorkflowImpl(
    workflow = workflow,
    props = props,
    onOutput = onOutput,
    actionSink = actionSink,
    parentFrame = null
  )

  override suspend fun <R> showScreen(
    screenFactory: BackStackScreenScope<R>.() -> Screen
  ): R = showScreenImpl(
    screenFactory = screenFactory,
    actionSink = actionSink,
    parentFrame = null
  )
}
