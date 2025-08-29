package com.squareup.sample.thingy

import com.squareup.workflow1.Sink
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

internal class BackStackWorkflowScopeImpl<PropsT, OutputT, R>(
  private val actionSink: Sink<WorkflowAction<PropsT, BackStackState, OutputT>>,
  coroutineScope: CoroutineScope,
  private val thisFrame: WorkflowFrame<*, *, *, *, R>,
  private val parentFrame: BackStackFrame<*>?,
) : BackStackWorkflowScope, CoroutineScope by coroutineScope {

  override suspend fun <ChildPropsT, ChildOutputT, R> showWorkflow(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    props: Flow<ChildPropsT>,
    onOutput: suspend BackStackWorkflowScope.(ChildOutputT) -> R
  ): R = showWorkflowImpl(
    workflow = workflow,
    props = props,
    onOutput = onOutput,
    actionSink = actionSink,
    parentFrame = thisFrame,
  )

  @Suppress("UNCHECKED_CAST")
  override suspend fun <R> showScreen(
    screenFactory: BackStackScreenScope<R>.() -> Screen
  ): R = showScreenImpl(
    screenFactory = screenFactory,
    actionSink = actionSink as Sink<WorkflowAction<Any?, BackStackState, OutputT>>,
    parentFrame = thisFrame,
  )

  override suspend fun cancelWorkflow(): Nothing {
    // If parent is null, goBack will not be exposed and will never be called.
    val parent = checkNotNull(parentFrame) { "goBack called on root scope" }
    actionSink.send(action("popTo") {
      state = state.popToFrame(parent)
    })
    thisFrame.cancelSelf()
  }
}
