package com.squareup.sample.thingy

import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Returns a [Workflow] that renders a [BackStackScreen] whose frames are controlled by the code
 * in [block].
 *
 * [block] can render child workflows by calling [RootScope.showWorkflow]. It can emit outputs to
 * its parent by calling [RootScope.emitOutput], and access its props via [RootScope.props].
 *
 * # Examples
 *
 * The backstack is represented by _nesting_ `showWorkflow` calls. Consider this example:
 * ```
 * thingyWorkflow {
 *   showWorkflow(child1) {
 *     showWorkflow(child2) {
 *       showWorkflow(child3) {
 *         // goBack()
 *       }
 *     }
 *   }
 * }
 * ```
 * This eventually represents a backstack of `[child1, child2, child3]`. `child2` will be pushed
 * onto the stack when `child1` emits an output, and `child3` pushed when `child2` emits. The
 * lambdas for `child2` and `child3` can call `goBack` to pop the stack and cancel the lambdas that
 * called their `showWorkflow`, until the next output is emitted.
 *
 * Contrast with calls in series:
 * ```
 * thingyWorkflow {
 *   showWorkflow(child1) { finishWith(Unit) }
 *   showWorkflow(child2) { finishWith(Unit) }
 *   showWorkflow(child3) { }
 * }
 * ```
 * `child1` will be shown immediately, but when it emits an output, instead of pushing `child2` onto
 * the stack, `child1` will be removed from the stack and replaced with `child2`.
 *
 * These can be combined:
 * ```
 * thingyWorkflow {
 *   showWorkflow(child1) {
 *     showWorkflow(child2) {
 *       // goBack(), or
 *       finishWith(Unit)
 *     }
 *     showWorkflow(child3) {
 *       // goBack()
 *     }
 *   }
 * }
 * ```
 * This code will show `child1` immediately, then when it emits an output show `child2`. When
 * `child2` emits an output, it can decide to call `goBack` to show `child1` again, or call
 * `finishWith` to replace itself with `child3`. `child3` can also call `goBack` to show `child`
 * again.
 */
public fun <PropsT, OutputT> thingyWorkflow(
  block: suspend RootScope<PropsT, OutputT>.() -> Unit
): Workflow<PropsT, OutputT, BackStackScreen<Screen>> = ThingyWorkflow(block)

@DslMarker
annotation class ThingyDsl

@ThingyDsl
public interface RootScope<PropsT, OutputT> : CoroutineScope {
  val props: StateFlow<PropsT>

  /**
   * Emits an output to the [thingyWorkflow]'s parent.
   */
  fun emitOutput(output: OutputT)

  /**
   * Starts rendering [workflow] and pushes its rendering onto the top of the backstack.
   *
   * Whenever [workflow] emits an output, [onOutput] is launched into a new coroutine. If one call
   * doesn't finish before another output is emitted, multiple callbacks can run concurrently.
   *
   * When [onOutput] calls [ShowWorkflowScope.finishWith], this workflow stops rendering, its
   * rendering is removed from the backstack, and any running output handlers are cancelled.
   *
   * Note that top-level workflows inside a [thingyWorkflow] cannot call
   * [ShowWorkflowChildScope.goBack] because the parent doesn't necessarily support that operation.
   */
  suspend fun <ChildPropsT, ChildOutputT, R> showWorkflow(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    props: ChildPropsT,
    onOutput: suspend ShowWorkflowScope<OutputT, R>.(output: ChildOutputT) -> Unit
  ): R
}

@ThingyDsl
public sealed interface ShowWorkflowScope<OutputT, R> : CoroutineScope {

  /**
   * Emits an output to the [thingyWorkflow]'s parent.
   */
  fun emitOutput(output: OutputT)

  /**
   * Causes the [showWorkflow] call that ran the output handler that was passed this scope to return
   * [value] and cancels any output handlers still running for that workflow. The workflow is
   * removed from the stack and will no longer be rendered.
   */
  suspend fun finishWith(value: R): Nothing

  /**
   * Starts rendering [workflow] and pushes its rendering onto the top of the backstack.
   *
   * Whenever [workflow] emits an output, [onOutput] is launched into a new coroutine. If one call
   * doesn't finish before another output is emitted, multiple callbacks can run concurrently.
   *
   * When [onOutput] calls [ShowWorkflowScope.finishWith] or [ShowWorkflowChildScope.goBack], this
   * workflow stops rendering, its rendering is removed from the backstack, and any running output
   * handlers are cancelled. [ShowWorkflowChildScope.goBack] will also cancel the output handler
   * of the parent workflow that called this [showWorkflow].
   */
  suspend fun <ChildPropsT, ChildOutputT, R> showWorkflow(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    props: ChildPropsT,
    onOutput: suspend ShowWorkflowChildScope<OutputT, R>.(output: ChildOutputT) -> Unit
  ): R
}

@ThingyDsl
public sealed interface ShowWorkflowChildScope<OutputT, R> : ShowWorkflowScope<OutputT, R> {
  /**
   * Removes all workflows started by the parent workflow's handler that invoked this [showWorkflow]
   * from the stack, and cancels that parent output handler coroutine (and thus all child workflow
   * coroutines as well).
   */
  suspend fun goBack(): Nothing
}

public suspend inline fun <OutputT, ChildOutputT, R> RootScope<*, OutputT>.showWorkflow(
  workflow: Workflow<Unit, ChildOutputT, Screen>,
  noinline onOutput: suspend ShowWorkflowScope<OutputT, R>.(output: ChildOutputT) -> Unit
): R = showWorkflow(workflow, props = Unit, onOutput)

public suspend inline fun <ChildPropsT> RootScope<*, *>.showWorkflow(
  workflow: Workflow<ChildPropsT, Nothing, Screen>,
  props: ChildPropsT,
): Nothing = showWorkflow(workflow, props = props) { error("Cannot call") }

public suspend inline fun RootScope<*, *>.showWorkflow(
  workflow: Workflow<Unit, Nothing, Screen>,
): Nothing = showWorkflow(workflow, props = Unit) { error("Cannot call") }

public suspend inline fun <OutputT, ChildOutputT, R> ShowWorkflowScope<OutputT, *>.showWorkflow(
  workflow: Workflow<Unit, ChildOutputT, Screen>,
  noinline onOutput: suspend ShowWorkflowChildScope<OutputT, R>.(output: ChildOutputT) -> Unit
): R = showWorkflow(workflow, props = Unit, onOutput)

public suspend inline fun <ChildPropsT> ShowWorkflowScope<*, *>.showWorkflow(
  workflow: Workflow<ChildPropsT, Nothing, Screen>,
  props: ChildPropsT,
): Nothing = showWorkflow(workflow, props = props) { error("Cannot call") }

public suspend inline fun ShowWorkflowScope<*, *>.showWorkflow(
  workflow: Workflow<Unit, Nothing, Screen>,
): Nothing = showWorkflow(workflow, props = Unit) { error("Cannot call") }

private class RootScopeImpl<PropsT, OutputT>(
  override val props: MutableStateFlow<PropsT>,
  private val actionSink: Sink<WorkflowAction<PropsT, ThingyState, OutputT>>,
  coroutineScope: CoroutineScope,
) : RootScope<PropsT, OutputT>, CoroutineScope by coroutineScope {

  override fun emitOutput(output: OutputT) {
    actionSink.send(action("emitOutput") {
      setOutput(output)
    })
  }

  override suspend fun <ChildPropsT, ChildOutputT, R> showWorkflow(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    props: ChildPropsT,
    onOutput: suspend ShowWorkflowScope<OutputT, R>.(ChildOutputT) -> Unit
  ): R = showWorkflow(
    workflow = workflow,
    props = props,
    onOutput = onOutput,
    actionSink = actionSink,
    parentFrame = null
  )
}

private class ShowWorkflowChildScopeImpl<PropsT, OutputT, R>(
  private val actionSink: Sink<WorkflowAction<PropsT, ThingyState, OutputT>>,
  coroutineScope: CoroutineScope,
  private val onFinish: (R) -> Unit,
  private val thisFrame: Frame<*, *, *, *, *>,
  private val parentFrame: Frame<*, *, *, *, *>?,
) : ShowWorkflowChildScope<OutputT, R>, CoroutineScope by coroutineScope {

  override fun emitOutput(output: OutputT) {
    actionSink.send(action("emitOutput") {
      setOutput(output)
    })
  }

  @Suppress("UNCHECKED_CAST")
  override suspend fun <ChildPropsT, ChildOutputT, R> showWorkflow(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    props: ChildPropsT,
    onOutput: suspend ShowWorkflowChildScope<OutputT, R>.(ChildOutputT) -> Unit
  ): R = showWorkflow(
    workflow = workflow,
    props = props,
    onOutput = onOutput,
    actionSink = actionSink,
    parentFrame = thisFrame,
  )

  override suspend fun finishWith(value: R): Nothing {
    onFinish(value)
    cancelSelf()
  }

  override suspend fun goBack(): Nothing {
    // If parent is null, goBack will not be exposed and will never be called.
    val parent = checkNotNull(parentFrame) { "goBack called on root scope" }
    parent.popTo()

    cancelSelf()
  }
}

private class Frame<PropsT, OutputT, ChildPropsT, ChildOutputT, R>(
  private val workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
  private val props: ChildPropsT,
  private val callerJob: Job,
  val frameScope: CoroutineScope,
  private val onOutput: suspend ShowWorkflowChildScopeImpl<PropsT, OutputT, R>.(ChildOutputT) -> Unit,
  private val actionSink: Sink<WorkflowAction<PropsT, ThingyState, OutputT>>,
  private val parent: Frame<*, *, *, *, *>?,
) {
  private val result = CompletableDeferred<R>(parent = frameScope.coroutineContext.job)

  suspend fun awaitResult(): R = result.await()

  fun renderWorkflow(
    context: StatefulWorkflow.RenderContext<PropsT, ThingyState, OutputT>
  ): Screen = context.renderChild(
    child = workflow,
    props = props,
    handler = ::onOutput
  )

  /**
   * Pops everything off the stack that comes after this.
   */
  fun popTo() {
    actionSink.send(action("popTo") {
      val stack = state.stack
      val index = stack.indexOf(this@Frame)
      check(index != -1) { "Frame was not in the stack!" }

      // Cancel all the frames we're about to drop, starting from the top.
      for (i in stack.lastIndex downTo index + 1) {
        // Don't just cancel the frame job, since that would only cancel output handlers the frame
        // is running. We want to cancel the whole parent's output handler that called showWorkflow,
        // in case the showWorkflow is in a try/catch that tries to make other suspending calls.
        stack[i].callerJob.cancel()
      }

      val newStack = stack.take(index + 1)
      state = state.copy(stack = newStack)
    })
  }

  private fun onOutput(output: ChildOutputT): WorkflowAction<PropsT, ThingyState, OutputT> {
    var canAcceptAction = true
    var action: WorkflowAction<PropsT, ThingyState, OutputT>? = null
    val sink = object : Sink<WorkflowAction<PropsT, ThingyState, OutputT>> {
      override fun send(value: WorkflowAction<PropsT, ThingyState, OutputT>) {
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
      val showScope = ShowWorkflowChildScopeImpl<PropsT, OutputT, R>(
        sink,
        coroutineScope = this,
        onFinish = {
          // This will eventually cancel the frame scope.
          result.complete(it)
          // TODO figure out how to coalesce this action into the one for showWorkflow. WorkStealingDispatcher?
          sink.send(action("unshowWorkflow") {
            state = state.removeFrame(this@Frame)
          })
        },
        thisFrame = this@Frame,
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

// TODO concurrent calls to this function on the same scope should cancel/remove prior calls.
//  Or maybe just enforce the same thing as Flow, only allow calls from the same job?
private suspend fun <PropsT, OutputT, ChildPropsT, ChildOutputT, R> showWorkflow(
  workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
  props: ChildPropsT,
  onOutput: suspend ShowWorkflowChildScopeImpl<PropsT, OutputT, R>.(ChildOutputT) -> Unit,
  actionSink: Sink<WorkflowAction<PropsT, ThingyState, OutputT>>,
  parentFrame: Frame<*, *, *, *, *>?,
): R {
  val callerContext = currentCoroutineContext()
  val callerJob = callerContext.job
  val frameScope = CoroutineScope(callerContext + Job(parent = callerJob))
  lateinit var frame: Frame<PropsT, OutputT, ChildPropsT, ChildOutputT, R>

  // Tell the workflow runtime to start rendering the new workflow.
  actionSink.sendAndAwaitApplication(action("showWorkflow") {
    frame = Frame(
      workflow = workflow,
      props = props,
      callerJob = callerJob,
      frameScope = frameScope,
      onOutput = onOutput,
      actionSink = actionSink,
      parent = parentFrame,
    )
    state = state.appendFrame(frame)
  })

  return try {
    frame.awaitResult()
  } finally {
    frameScope.cancel()
    actionSink.send(action("unshowWorkflow") {
      state = state.removeFrame(frame)
    })
  }
}

private class ThingyState(
  val stack: List<Frame<*, *, *, *, *>>,
  val props: MutableStateFlow<Any?>,
) {

  fun copy(stack: List<Frame<*, *, *, *, *>> = this.stack) = ThingyState(
    stack = stack,
    props = props
  )

  fun appendFrame(frame: Frame<*, *, *, *, *>) = copy(stack = stack + frame)
  fun removeFrame(frame: Frame<*, *, *, *, *>) = copy(stack = stack - frame)
}

private class ThingyWorkflow<PropsT, OutputT>(
  private val block: suspend RootScope<PropsT, OutputT>.() -> Unit
) : StatefulWorkflow<
  PropsT,
  ThingyState,
  OutputT,
  BackStackScreen<Screen>
  >() {

  override fun initialState(
    props: PropsT,
    snapshot: Snapshot?
  ): ThingyState {
    return ThingyState(
      stack = emptyList(),
      props = MutableStateFlow(props)
    )
  }

  override fun onPropsChanged(
    old: PropsT,
    new: PropsT,
    state: ThingyState
  ): ThingyState = state.apply {
    props.value = new
  }

  override fun render(
    renderProps: PropsT,
    renderState: ThingyState,
    context: RenderContext<PropsT, ThingyState, OutputT>
  ): BackStackScreen<Screen> {
    context.runningSideEffect("main") {
      @Suppress("UNCHECKED_CAST")
      val scope = RootScopeImpl(
        props = renderState.props as MutableStateFlow<PropsT>,
        actionSink = context.actionSink,
        coroutineScope = this,
      )
      block(scope)
    }

    val renderings = renderState.stack.map { frame ->
      @Suppress("UNCHECKED_CAST")
      (frame as Frame<PropsT, OutputT, *, *, *>).renderWorkflow(context)
    }

    // TODO show a loading screen if renderings is empty.
    return renderings.toBackStackScreen()
  }

  override fun snapshotState(state: ThingyState): Snapshot? = null
}

private suspend fun cancelSelf(): Nothing {
  val job = currentCoroutineContext().job
  job.cancel()
  job.ensureActive()
  error("Nonsense")
}

private suspend fun <
  PropsT,
  StateT,
  OutputT
  > Sink<WorkflowAction<PropsT, StateT, OutputT>>.sendAndAwaitApplication(
  action: WorkflowAction<PropsT, StateT, OutputT>
) {
  suspendCancellableCoroutine { continuation ->
    val resumingAction = object : WorkflowAction<PropsT, StateT, OutputT>() {
      // Pipe through debugging name to the original action.
      override val debuggingName: String
        get() = action.debuggingName

      override fun Updater.apply() {
        // Don't execute anything if the caller was cancelled while we were in the queue.
        if (!continuation.isActive) return

        with(action) {
          // Forward our Updater to the real action.
          apply()
        }
        continuation.resume(Unit)
      }
    }
    send(resumingAction)
  }
}
