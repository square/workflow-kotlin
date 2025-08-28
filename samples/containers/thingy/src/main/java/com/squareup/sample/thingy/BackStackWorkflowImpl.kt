package com.squareup.sample.thingy

import com.squareup.workflow1.SessionWorkflow
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.BackStackScreen
import com.squareup.workflow1.ui.navigation.toBackStackScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

@OptIn(WorkflowExperimentalApi::class)
internal class BackStackWorkflowImpl<PropsT, OutputT>(
  private val workflow: BackStackWorkflow<PropsT, OutputT>
) : SessionWorkflow<
  PropsT,
  BackStackState,
  OutputT,
  BackStackScreen<Screen>
  >() {

  override fun initialState(
    props: PropsT,
    snapshot: Snapshot?,
    workflowScope: CoroutineScope
  ): BackStackState {
    val propsFlow = MutableStateFlow(props)

    @Suppress("UNCHECKED_CAST")
    val initialState = BackStackState(
      stack = emptyList(),
      props = propsFlow as MutableStateFlow<Any?>
    )

    // TODO move this into the launch call so the scope is correct (use this instead of
    //  workflowScope).
    val scope = BackStackScopeImpl<OutputT>(
      coroutineScope = workflowScope,
    )
    workflowScope.launch(start = CoroutineStart.UNDISPATCHED) {
      with(workflow) {
        scope.runBackStack(propsFlow)
      }
    }

    // TODO gather initial state from the coroutine

    return initialState
  }

  override fun onPropsChanged(
    old: PropsT,
    new: PropsT,
    state: BackStackState
  ): BackStackState = state.apply {
    setProps(new)
    // TODO gather updated state from coroutine
  }

  override fun render(
    renderProps: PropsT,
    renderState: BackStackState,
    context: RenderContext<PropsT, BackStackState, OutputT>
  ): BackStackScreen<Screen> {

    val renderings = renderState.mapFrames { frame ->
      when (frame) {
        is WorkflowFrame<*, *, *, *, *> -> {
          @Suppress("UNCHECKED_CAST")
          (frame as WorkflowFrame<PropsT, OutputT, *, *, *>).renderWorkflow(context)
        }

        is ScreenFrame<*, *> -> {
          frame.screen
        }
      }
    }

    // TODO show a loading screen if renderings is empty.
    return renderings.toBackStackScreen()
  }

  override fun snapshotState(state: BackStackState): Snapshot? = null
}

internal class BackStackScopeImpl<OutputT>(
  coroutineScope: CoroutineScope,
) : BackStackScope<OutputT>, CoroutineScope by coroutineScope {
  lateinit var actionSink: Sink<WorkflowAction<Any?, BackStackState, OutputT>>

  override fun emitOutput(output: OutputT) {
    actionSink.send(action("emitOutput") {
      setOutput(output)
    })
  }

  override suspend fun <ChildPropsT, ChildOutputT, R> showWorkflow(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    props: Flow<ChildPropsT>,
    onOutput: suspend BackStackNestedScope<OutputT, R>.(ChildOutputT) -> Unit
  ): R = showWorkflow(
    workflow = workflow,
    props = props,
    onOutput = onOutput,
    actionSink = actionSink,
    parentFrame = null
  )

  override suspend fun <R> showScreen(
    screenFactory: BackStackNestedScope<OutputT, R>.() -> Screen
  ): R = showScreenImpl(
    screenFactory = screenFactory,
    actionSink = actionSink,
    parentFrame = null
  )
}

private class BackStackNestedScopeImpl<PropsT, OutputT, R>(
  private val actionSink: Sink<WorkflowAction<PropsT, BackStackState, OutputT>>,
  coroutineScope: CoroutineScope,
  private val thisFrame: Frame<R>,
  private val parentFrame: Frame<*>?,
) : BackStackNestedScope<OutputT, R>, CoroutineScope by coroutineScope {

  override fun emitOutput(output: OutputT) {
    actionSink.send(action("emitOutput") {
      setOutput(output)
    })
  }

  @Suppress("UNCHECKED_CAST")
  override suspend fun <ChildPropsT, ChildOutputT, R> showWorkflow(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    props: Flow<ChildPropsT>,
    onOutput: suspend BackStackNestedScope<OutputT, R>.(ChildOutputT) -> Unit
  ): R = showWorkflow(
    workflow = workflow,
    props = props,
    onOutput = onOutput,
    actionSink = actionSink,
    parentFrame = thisFrame,
  )

  @Suppress("UNCHECKED_CAST")
  override suspend fun <R> showScreen(
    screenFactory: BackStackNestedScope<OutputT, R>.() -> Screen
  ): R = showScreenImpl(
    screenFactory = screenFactory,
    actionSink = actionSink as Sink<WorkflowAction<Any?, BackStackState, OutputT>>,
    parentFrame = thisFrame,
  )

  override suspend fun finishWith(value: R): Nothing {
    // TODO figure out how to coalesce this action into the one for showWorkflow. WorkStealingDispatcher?
    actionSink.send(action("finishFrame") {
      state = state.removeFrame(thisFrame)
    })
    thisFrame.finishWith(value)
  }

  override suspend fun goBack(): Nothing {
    // If parent is null, goBack will not be exposed and will never be called.
    val parent = checkNotNull(parentFrame) { "goBack called on root scope" }
    actionSink.send(action("popTo") {
      state = state.popToFrame(parent)
    })
    thisFrame.cancelSelf()
  }
}

internal sealed interface Frame<R> {
  fun cancelCaller()
  suspend fun awaitResult(): R
  suspend fun finishWith(value: R): Nothing
  suspend fun cancelSelf(): Nothing
}

/**
 * Represents a call to [BackStackScope.showWorkflow].
 */
internal class WorkflowFrame<PropsT, OutputT, ChildPropsT, ChildOutputT, R> private constructor(
  private val workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
  private val props: ChildPropsT,
  private val callerJob: Job,
  private val frameScope: CoroutineScope,
  private val onOutput: suspend BackStackNestedScope<OutputT, R>.(ChildOutputT) -> Unit,
  private val actionSink: Sink<WorkflowAction<PropsT, BackStackState, OutputT>>,
  private val parent: Frame<*>?,
  private val result: CompletableDeferred<R>,
) : Frame<R> {

  constructor(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    initialProps: ChildPropsT,
    callerJob: Job,
    frameScope: CoroutineScope,
    onOutput: suspend BackStackNestedScope<OutputT, R>.(ChildOutputT) -> Unit,
    actionSink: Sink<WorkflowAction<PropsT, BackStackState, OutputT>>,
    parent: Frame<*>?,
  ) : this(
    workflow = workflow,
    props = initialProps,
    callerJob = callerJob,
    frameScope = frameScope,
    onOutput = onOutput,
    actionSink = actionSink,
    parent = parent,
    result = CompletableDeferred(parent = frameScope.coroutineContext.job)
  )

  fun copy(
    props: ChildPropsT = this.props,
  ): WorkflowFrame<PropsT, OutputT, ChildPropsT, ChildOutputT, R> = WorkflowFrame(
    workflow = workflow,
    props = props,
    callerJob = callerJob,
    frameScope = frameScope,
    onOutput = onOutput,
    actionSink = actionSink,
    parent = parent,
    result = result
  )

  override suspend fun awaitResult(): R = result.await()

  override fun cancelCaller() {
    callerJob.cancel()
  }

  override suspend fun finishWith(value: R): Nothing {
    result.complete(value)
    cancelSelf()
  }

  override suspend fun cancelSelf(): Nothing {
    frameScope.cancel()
    val currentContext = currentCoroutineContext()
    currentContext.cancel()
    currentContext.ensureActive()
    error("Nonsense")
  }

  fun renderWorkflow(
    context: StatefulWorkflow.RenderContext<PropsT, BackStackState, OutputT>
  ): Screen = context.renderChild(
    child = workflow,
    props = props,
    handler = ::onOutput
  )

  private fun onOutput(output: ChildOutputT): WorkflowAction<PropsT, BackStackState, OutputT> {
    var canAcceptAction = true
    var action: WorkflowAction<PropsT, BackStackState, OutputT>? = null
    val sink = object : Sink<WorkflowAction<PropsT, BackStackState, OutputT>> {
      override fun send(value: WorkflowAction<PropsT, BackStackState, OutputT>) {
        val sendToSink = synchronized(result) {
          if (canAcceptAction) {
            action = value
            canAcceptAction = false
            false
          } else {
            true
          }
        }
        if (sendToSink) {
          actionSink.send(value)
        }
      }
    }

    // Run synchronously until first suspension point since in many cases it will immediately
    // either call showWorkflow, finishWith, or goBack, and so then we can just return that action
    // immediately instead of needing a whole separate render pass.
    frameScope.launch(start = CoroutineStart.UNDISPATCHED) {
      val showScope = BackStackNestedScopeImpl(
        actionSink = sink,
        coroutineScope = this,
        thisFrame = this@WorkflowFrame,
        parentFrame = parent
      )
      onOutput(showScope, output)
    }

    // Once the coroutine has suspended, all sends must go to the real sink.
    return synchronized(result) {
      canAcceptAction = false
      action ?: WorkflowAction.noAction()
    }
  }
}

/**
 * Represents a call to [BackStackScope.showScreen].
 */
internal class ScreenFrame<OutputT, R>(
  private val callerJob: Job,
  private val frameScope: CoroutineScope,
  private val actionSink: Sink<WorkflowAction<Any?, BackStackState, OutputT>>,
  private val parent: Frame<*>?,
) : Frame<R> {
  private val result = CompletableDeferred<R>()

  lateinit var screen: Screen
    private set

  fun initScreen(screenFactory: BackStackNestedScope<OutputT, R>.() -> Screen) {
    val factoryScope = BackStackNestedScopeImpl<Any?, OutputT, R>(
      actionSink = actionSink,
      coroutineScope = frameScope,
      thisFrame = this,
      parentFrame = parent
    )
    screen = screenFactory(factoryScope)
  }

  override suspend fun awaitResult(): R = result.await()

  override fun cancelCaller() {
    callerJob.cancel()
  }

  override suspend fun finishWith(value: R): Nothing {
    result.complete(value)
    cancelSelf()
  }

  override suspend fun cancelSelf(): Nothing {
    frameScope.cancel()
    val currentContext = currentCoroutineContext()
    currentContext.cancel()
    currentContext.ensureActive()
    error("Nonsense")
  }
}

// TODO concurrent calls to this function on the same scope should cancel/remove prior calls.
private suspend fun <PropsT, OutputT, ChildPropsT, ChildOutputT, R> showWorkflow(
  workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
  props: Flow<ChildPropsT>,
  onOutput: suspend BackStackNestedScope<OutputT, R>.(ChildOutputT) -> Unit,
  actionSink: Sink<WorkflowAction<PropsT, BackStackState, OutputT>>,
  parentFrame: Frame<*>?,
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

private suspend fun <OutputT, R> showScreenImpl(
  screenFactory: BackStackNestedScope<OutputT, R>.() -> Screen,
  actionSink: Sink<WorkflowAction<Any?, BackStackState, OutputT>>,
  parentFrame: Frame<*>?,
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

internal class BackStackState(
  private val stack: List<Frame<*>>,
  private val props: MutableStateFlow<Any?>,
) {

  fun copy(stack: List<Frame<*>> = this.stack) = BackStackState(
    stack = stack,
    props = props
  )

  fun setProps(props: Any?) {
    this.props.value = props
  }

  fun appendFrame(frame: Frame<*>) = copy(stack = stack + frame)
  fun removeFrame(frame: Frame<*>) = copy(stack = stack - frame)

  fun popToFrame(frame: Frame<*>): BackStackState {
    val index = stack.indexOf(frame)
    check(index != -1) { "Frame was not in the stack!" }

    // Cancel all the frames we're about to drop, starting from the top.
    for (i in stack.lastIndex downTo index + 1) {
      // Don't just cancel the frame job, since that would only cancel output handlers the frame
      // is running. We want to cancel the whole parent's output handler that called showWorkflow,
      // in case the showWorkflow is in a try/catch that tries to make other suspending calls.
      stack[i].cancelCaller()
    }

    val newStack = stack.take(index + 1)
    return copy(stack = newStack)
  }

  fun <ChildPropsT> setFrameProps(
    frame: WorkflowFrame<*, *, ChildPropsT, *, *>,
    newProps: ChildPropsT
  ): BackStackState {
    val stack = stack.toMutableList()
    val myIndex = stack.indexOf(frame)
    if (myIndex == -1) {
      // Frame has been removed from the stack, so just no-op.
      return this
    }
    stack[myIndex] = frame.copy(props = newProps)
    return copy(stack = stack)
  }

  inline fun <R> mapFrames(block: (Frame<*>) -> R): List<R> = stack.map(block)
}
