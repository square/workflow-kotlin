package com.squareup.sample.thingy

import com.squareup.workflow1.StatefulWorkflow.RenderContext
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.ui.Screen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class BackStackNode(
  private val actionQueue: ActionQueue,
  private val key: String,
  parentJob: Job,
  private val dispatcher: BackStackDispatcher,
  private val onCancel: () -> Unit,
  private val onEmitOutputAction: () -> Unit
) : BackStackScope, BackStackScreenScope<Any?> {

  private val result = CompletableDeferred<Any?>(parent = parentJob)
  private val workerScope = CoroutineScope(result + dispatcher)

  private val activeChildLock = Any()

  /** All access must be guarded by [activeChildLock]. */
  private var activeChild: BackStackNode? = null

  private var childWorkflowKeyCounter = AtomicInt(0)

  private fun createNewChildKey(): String {
    val id = childWorkflowKeyCounter.fetchAndAdd(1)
    return "$key.$id"
  }

  /**
   * Tracks how many calls to [launch] are currently running. All access must be guarded by
   * [activeChildLock].
   *
   * The node is idle when this value > 0 and [activeChild] is null.
   */
  private var workers = 0

  private val isIdle: Boolean
    get() = synchronized(activeChildLock) {
      workers > 0 && activeChild == null
    }

  override suspend fun <ChildPropsT, ChildOutputT, R> showWorkflow(
    workflow: Workflow<ChildPropsT, ChildOutputT, Screen>,
    props: Flow<ChildPropsT>,
    onOutput: suspend BackStackWorkflowScope.(output: ChildOutputT) -> R
  ): R = showNode { workflowNode ->
    props.map { props ->
      object : BackStackFrame {
        override val node: BackStackNode
          get() = workflowNode

        override fun render(context: RenderContext<Any?, BackStackState, Any?>): Screen =
          context.renderChild(
            child = workflow,
            key = workflowNode.key,
            props = props,
            handler = { output ->
              dispatcher.runThenDispatchImmediately {
                workflowNode.launch {
                  val scope = object :
                    BackStackWorkflowScope,
                    BackStackScope by workflowNode,
                    CoroutineScope by this {

                    override suspend fun cancelWorkflow(): Nothing {
                      workflowNode.onCancel()
                      currentCoroutineContext().ensureActive()
                      error(
                        "cancelWorkflow() called from a coroutine that was not a child of the " +
                          "BackStackWorkflowScope"
                      )
                    }
                  }
                  onOutput(scope, output)
                }
              }
              @Suppress("UNCHECKED_CAST")
              actionQueue.consumeToAction(onEmitOutputAction) as
                WorkflowAction<Any?, BackStackState, Any?>
            }
          )
      }
    }
  }

  override suspend fun <R> showScreen(
    screenFactory: BackStackScreenScope<R>.() -> Screen
  ): R = showNode { screenNode ->
    flow {
      @Suppress("UNCHECKED_CAST")
      val screen = screenFactory(screenNode as BackStackScreenScope<R>)
      emit(object : BackStackFrame {
        override val node: BackStackNode
          get() = screenNode

        override fun render(context: RenderContext<Any?, BackStackState, Any?>): Screen = screen
      })
    }
  }

  override fun launch(block: suspend CoroutineScope.() -> Unit) {
    workerScope.launch {
      synchronized(activeChildLock) { workers++ }
      updateFrame()

      try {
        // Need a child scope here to wait for any coroutines launched inside block to finish before
        // decrementing workers.
        coroutineScope {
          block()
        }
      } finally {
        synchronized(activeChildLock) { workers-- }
        updateFrame()
      }
    }
  }

  private suspend fun <R> showNode(
    block: CoroutineScope.(BackStackNode) -> Flow<BackStackFrame>
  ): R = withContext(dispatcher) {
    val childJob = coroutineContext.job
    val childNode = BackStackNode(
      actionQueue = actionQueue,
      key = createNewChildKey(),
      parentJob = childJob,
      dispatcher = dispatcher,
      onCancel = result::cancelChildren,
      onEmitOutputAction = onEmitOutputAction,
    )

    withActiveChild(childNode) {
      val frames = block(childNode)
      showFrames(childNode, frames, frameScope = this) {
        @Suppress("UNCHECKED_CAST")
        childNode.result.await() as R
      }
    }
  }

  private suspend inline fun <R> showFrames(
    childNode: BackStackNode,
    frames: Flow<BackStackFrame>,
    frameScope: CoroutineScope,
    crossinline block: suspend () -> R
  ): R {
    try {
      frames
        .onEach { newFrame ->
          childNode.updateFrame { newFrame }
        }
        .launchIn(frameScope)

      return block()
    } finally {
      // Remove this node's frame.
      childNode.updateFrame { null }
    }
  }

  override fun continueWith(value: Any?) {
    if (!result.complete(value)) {
      error("Tried to finish with $value but already finished")
    }
  }

  override fun cancelScreen() {
    onCancel()
  }

  private suspend inline fun <R> withActiveChild(
    child: BackStackNode,
    block: () -> R
  ): R {
    val oldChild = synchronized(activeChildLock) {
      activeChild.also { activeChild = child }
    }
    oldChild?.result?.cancelAndJoin()

    try {
      return block()
    } finally {
      synchronized(activeChildLock) {
        // If we're being canceled by another withActiveChild call, don't overwrite the new child.
        if (activeChild === child) {
          activeChild = null
        }
      }
    }
  }

  private fun updateFrame(
    update: ((BackStackFrame?) -> BackStackFrame?)? = null
  ) {
    val isIdle = isIdle
    actionQueue.enqueueStateTransformation { frames ->
      val index = frames.indexOfFirst { it.node === this }
      val previousFrame = if (index == -1) null else frames[index]
      val newFrame = if (update != null) update(previousFrame) else previousFrame
      if (newFrame == null) {
        frames.removeAt(index)
      } else {
        frames[index] = if (isIdle) newFrame.withIdle() else newFrame
      }
    }
  }
}
