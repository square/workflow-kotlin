package com.squareup.sample.thingy

import com.squareup.workflow1.Sink
import com.squareup.workflow1.StatefulWorkflow.RenderContext
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowAction.Companion
import com.squareup.workflow1.ui.Screen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

internal sealed interface BackStackFrame<R> {
  fun cancelCaller()
  suspend fun awaitResult(): R
  suspend fun cancelSelf(): Nothing
  fun cancel()
}

/**
 * Represents a call to [BackStackScope.showWorkflow].
 */
internal class WorkflowFrame<PropsT, OutputT, ChildPropsT, ChildOutputT, R> private constructor(
  private val workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
  private val props: ChildPropsT,
  private val callerJob: Job,
  private val frameScope: CoroutineScope,
  private val onOutput: suspend BackStackWorkflowScope.(ChildOutputT) -> R,
  private val actionSink: Sink<WorkflowAction<PropsT, BackStackState, OutputT>>,
  private val parent: BackStackFrame<*>?,
  private val result: CompletableDeferred<R>,
) : BackStackFrame<R> {

  constructor(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    initialProps: ChildPropsT,
    callerJob: Job,
    frameScope: CoroutineScope,
    onOutput: suspend BackStackWorkflowScope.(ChildOutputT) -> R,
    actionSink: Sink<WorkflowAction<PropsT, BackStackState, OutputT>>,
    parent: BackStackFrame<*>?,
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

  private suspend fun finishWith(value: R): Nothing {
    result.complete(value)
    cancelSelf()
  }

  override suspend fun cancelSelf(): Nothing {
    cancel()
    val currentContext = currentCoroutineContext()
    currentContext.cancel()
    currentContext.ensureActive()
    error("Nonsense")
  }

  override fun cancel() {
    frameScope.cancel()
  }

  fun renderWorkflow(
    context: RenderContext<PropsT, BackStackState, OutputT>
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
    frameScope.launch(start = UNDISPATCHED) {
      val showScope = BackStackWorkflowScopeImpl(
        actionSink = sink,
        coroutineScope = this,
        thisFrame = this@WorkflowFrame,
        parentFrame = parent
      )
      finishWith(onOutput(showScope, output))
    }
    // TODO collect WorkflowAction

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
  private val parent: BackStackFrame<*>?,
) : BackStackFrame<R> {
  private val result = CompletableDeferred<R>()

  lateinit var screen: Screen
    private set

  fun initScreen(screenFactory: BackStackScreenScope<R>.() -> Screen) {
    val factoryScope = BackStackScreenScopeImpl<Any?, OutputT, R>(
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

  fun continueWith(value: R) {
    result.complete(value)
    cancel()
  }

  override suspend fun cancelSelf(): Nothing {
    cancel()
    val currentContext = currentCoroutineContext()
    currentContext.cancel()
    currentContext.ensureActive()
    error("Nonsense")
  }

  override fun cancel() {
    frameScope.cancel()
  }
}
