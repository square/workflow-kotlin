package com.squareup.sample.thingy

import com.squareup.workflow1.Sink
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.Screen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

// TODO Both these functions should cancel any previous calls to either function from the same frame
//  (but not necessarily cancel the handler scope) before doing their own thing.

// TODO Both these functions should withContext to the special dispatcher, to ensure its onIdle
//  callback runs after their state mutations are enqueued.

internal suspend fun <PropsT, OutputT, ChildPropsT, ChildOutputT, R> showWorkflowImpl(
  workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
  props: Flow<ChildPropsT>,
  onOutput: suspend BackStackWorkflowScope.(ChildOutputT) -> R,
  actionSink: Sink<WorkflowAction<PropsT, BackStackState, OutputT>>,
  parentFrame: BackStackFrame<*>?,
): R {
  val callerContext = currentCoroutineContext()
  val callerJob = callerContext.job
  val frameScope = CoroutineScope(callerContext + Job(parent = callerJob))
  lateinit var frame: WorkflowFrame<PropsT, OutputT, ChildPropsT, ChildOutputT, R>

  val initialProps = CompletableDeferred<ChildPropsT>()
  val readyForPropUpdates = Job()
  frameScope.launch {
    props.collect { newProps ->
      if (initialProps.isActive) {
        initialProps.complete(newProps)
      } else {
        // Ensure the frame has actually been added to the stack.
        readyForPropUpdates.join()
        actionSink.send(action("setProps") {
          state = state.setFrameProps(frame, newProps)
        })
      }
    }
  }
  frame = WorkflowFrame(
    workflow = workflow,
    initialProps = initialProps.await(),
    callerJob = callerJob,
    frameScope = frameScope,
    onOutput = onOutput,
    actionSink = actionSink,
    parent = parentFrame,
  )

  // Tell the workflow runtime to start rendering the new workflow.
  actionSink.send(action("showWorkflow") {
    state = state.appendFrame(frame)
  })
  // Allow the props collector to send more prop update actions. Even though the initial action
  // hasn't run yet, any future actions will be enqueued after it, so it's safe.
  readyForPropUpdates.complete()

  return try {
    frame.awaitResult()
  } finally {
    frameScope.cancel()
    actionSink.send(action("unshowWorkflow") {
      state = state.removeFrame(frame)
    })
  }
}

internal suspend fun <OutputT, R> showScreenImpl(
  screenFactory: BackStackScreenScope<R>.() -> Screen,
  actionSink: Sink<WorkflowAction<Any?, BackStackState, OutputT>>,
  parentFrame: BackStackFrame<*>?,
): R {
  val callerContext = currentCoroutineContext()
  val callerJob = callerContext.job
  val frameScope = CoroutineScope(callerContext + Job(parent = callerJob))

  val frame = ScreenFrame<OutputT, R>(
    callerJob = callerJob,
    frameScope = frameScope,
    actionSink = actionSink,
    parent = parentFrame,
  )
  frame.initScreen(screenFactory)

  // Tell the workflow runtime to start rendering the new workflow.
  actionSink.send(action("showScreen") {
    state = state.appendFrame(frame)
  })

  return try {
    frame.awaitResult()
  } finally {
    frameScope.cancel()
    actionSink.send(action("unshowScreen") {
      state = state.removeFrame(frame)
    })
  }
}
