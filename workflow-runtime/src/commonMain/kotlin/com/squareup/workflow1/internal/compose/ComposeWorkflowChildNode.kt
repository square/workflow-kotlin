package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collection.MutableVector
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.ActionsExhausted
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.LocalWorkflowComposableRenderer
import com.squareup.workflow1.compose.WorkflowComposableRenderer
import com.squareup.workflow1.identifier
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.createId
import com.squareup.workflow1.internal.requireSend
import com.squareup.workflow1.trace
import com.squareup.workflow1.workflowSessionToString
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

private const val OUTPUT_QUEUE_LIMIT = 1_000

/**
 * Representation and implementation of a single [ComposeWorkflow] inside a
 * [ComposeWorkflowNodeAdapter].
 */
@Stable
@OptIn(WorkflowExperimentalApi::class)
internal class ComposeWorkflowChildNode<PropsT, OutputT, RenderingT>(
  override val id: WorkflowNodeId,
  initialProps: PropsT,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  private val parentNode: ComposeWorkflowChildNode<*, *, *>?,
  override val parent: WorkflowSession?,
  override val workflowTracer: WorkflowTracer?,
  override val runtimeConfig: RuntimeConfig,
  private val interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  private val idCounter: IdCounter? = null,
  private val emitAppliedActionToParent: (ActionApplied<OutputT>) -> ActionProcessingResult,
) :
  ComposeChildNode<PropsT, OutputT, RenderingT>,
  WorkflowSession,
  WorkflowComposableRenderer,
  CoroutineScope,
  RememberObserver {

  // We don't need to create our own job because unlike for WorkflowNode, the baseContext already
  // has a dedicated job: either from the adapter (for root compose workflow), or from
  // rememberCoroutineScope().
  override val coroutineContext: CoroutineContext = baseContext +
    CoroutineName(id.toString())

  // WorkflowSession properties
  override val identifier: WorkflowIdentifier get() = id.identifier
  override val renderKey: String get() = id.name
  override val sessionId: Long = idCounter.createId()

  /** This does not need to be a snapshot state object, it's only set again by [snapshot]. */
  private var snapshotCache = snapshot?.childTreeSnapshots
  private val saveableStateRegistry: SaveableStateRegistry

  // Don't allocate childNodes list until a child is rendered, leaf node optimization.
  private var childNodes: MutableVector<ComposeChildNode<*, *, *>>? = null

  private val outputsChannel = Channel<OutputT>(capacity = OUTPUT_QUEUE_LIMIT)

  // TODO this should be a ThreadLocal in case emitOutput is called from a different thread during
  //  an action cascade.
  private var onEmitOutputOverride: ((OutputT) -> Unit)? = null
  private val onEmitOutput: (OutputT) -> Unit = { output ->
    val override = onEmitOutputOverride
    if (override != null) {
      override(output)
    } else {
      sendOutputToChannel(output)
    }
  }

  private var lastProps by mutableStateOf(initialProps)

  /**
   * Function invoked when [onNextAction] receives an output from [outputsChannel].
   */
  private val processOutputFromChannel: (OutputT) -> ActionProcessingResult = { output ->
    log("got output from channel: $output")

    val applied = ActionApplied(
      output = WorkflowOutput(output),
      // We can't know if any state read by this workflow composable specifically changed, but the
      // ComposeWorkflowNodeAdapter hosting the composition will modify this as necessary based on
      // whether _any_ state in the composition changed.
      stateChanged = false
    )

    // Invoke the parent's handler to propagate the output up the workflow tree.
    log("sending output to parent: $applied")
    emitAppliedActionToParent(applied).also {
      log("finished sending output to parent, result was: $it")
    }
  }

  init {
    interceptor.onSessionStarted(workflowScope = this, session = this)

    val workflowSnapshot = snapshot?.workflowSnapshot
    var restoredRegistry: SaveableStateRegistry? = null
    // Don't care if the interceptor returns a state value, our state is stored in the composition.
    interceptor.onInitialState(
      props = initialProps,
      snapshot = workflowSnapshot,
      workflowScope = this,
      session = this,
      proceed = { _, innerSnapshot, _ ->
        restoredRegistry = restoreSaveableStateRegistryFromSnapshot(innerSnapshot)
        ComposeWorkflowState
      }
    )
    // Can't assign directly in proceed because the compiler can't guarantee it's ran during the
    // initialization.
    saveableStateRegistry = restoredRegistry ?: restoreSaveableStateRegistryFromSnapshot(null)
  }

  override fun toString(): String = workflowSessionToString()

  @Composable
  override fun produceRendering(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT
  ): RenderingT {
    // No need to key anything on `this`, since either this is at the root of the composition, or
    // inside a renderChild call and renderChild does the keying.
    log("rendering workflow: props=$props")
    workflow as ComposeWorkflow

    workflowTracer?.beginSection("ComposeChildNotifyInterceptorWhenPropsChange")
    notifyInterceptorWhenPropsChanged(props)
    workflowTracer?.endSection()

    workflowTracer?.beginSection("ComposeChildWithCompositionLocals")
    return withCompositionLocals(
      LocalSaveableStateRegistry provides saveableStateRegistry,
      LocalWorkflowComposableRenderer provides this
    ) {
      workflowTracer?.beginSection("ComposeChildProduceSelfRendering")
      workflow.produceRendering(props, onEmitOutput)
        .also { workflowTracer?.endSection() }
      // interceptor.onRenderComposeWorkflow(
      //   renderProps = props,
      //   emitOutput = onEmitOutput,
      //   session = this,
      //   proceed = { innerProps, innerEmitOutput ->
      //     _DO_NOT_USE_invokeComposeWorkflowProduceRendering(workflow, innerProps, innerEmitOutput)
      //   }
      // )
    }.also { workflowTracer?.endSection() }
  }

  @ReadOnlyComposable
  @NonRestartableComposable
  @Composable
  private fun notifyInterceptorWhenPropsChanged(newProps: PropsT) {
    // Don't both asking the composition to track reads of lastProps since this is the only function
    // that will every write to it.
    Snapshot.withoutReadObservation {
      if (lastProps != newProps) {
        interceptor.onPropsChanged(
          old = lastProps,
          new = newProps,
          state = ComposeWorkflowState,
          session = this,
          proceed = { _, _, _ -> ComposeWorkflowState },
        )
        lastProps = newProps
      }
    }
  }

  @Composable
  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    childWorkflow: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    onOutput: ((ChildOutputT) -> Unit)?
  ): ChildRenderingT {
    // All child state should be preserved across renders if the Workflow instance changes but has
    // the same identifier.
    val childIdentifier = childWorkflow.identifier
    return key(childIdentifier) {
      workflowTracer?.beginSection("ComposeChildRememberChildNode")
      val childNode = rememberComposeChildNode(
        childWorkflow = childWorkflow,
        childIdentifier = childIdentifier,
        initialProps = props,
        onOutput = onOutput
      )
      workflowTracer?.endSection()

      workflowTracer?.beginSection("ComposeChildProduceChildRendering")
      return@key childNode.produceRendering(childWorkflow, props)
        .also { workflowTracer?.endSection() }
    }
  }

  override fun snapshot(): TreeSnapshot {
    // Get rid of any snapshots that weren't applied on the first render pass.
    // They belong to children that were saved but not restarted.
    snapshotCache = null

    return interceptor.onSnapshotStateWithChildren(
      session = this,
      proceed = {
        val workflowSnapshot = interceptor.onSnapshotState(
          state = ComposeWorkflowState,
          session = this,
          proceed = {
            saveSaveableStateRegistryToSnapshot(saveableStateRegistry)
          }
        )

        TreeSnapshot(
          workflowSnapshot = workflowSnapshot,
          childTreeSnapshots = ::createChildSnapshots
        )
      }
    )
  }

  /**
   * Track child nodes for snapshotting.
   * NOTE: While the effect will run after composition, it will run as part of the compose
   * frame, so the child will be registered before ComposeWorkflowNodeAdapter's render method
   * returns.
   */
  override fun onRemembered() {
    parentNode?.addChildNode(this)
  }

  override fun onForgotten() {
    parentNode?.removeChildNode(this)
  }

  override fun onAbandoned() = Unit

  override fun registerTreeActionSelectors(selector: SelectBuilder<ActionProcessingResult>) {
    childNodes?.forEach { child ->
      child.registerTreeActionSelectors(selector)
    }

    with(selector) {
      outputsChannel.onReceive(processOutputFromChannel)
    }
  }

  override fun applyNextAvailableTreeAction(skipDirtyNodes: Boolean): ActionProcessingResult {
    // First let any children with pending actions process them.
    childNodes?.forEach { child ->
      val result = child.applyNextAvailableTreeAction(skipDirtyNodes)
      if (result != ActionsExhausted) {
        return result
      }
    }

    // If none of our children had any actions to process, then we can process any outputs of our
    // own.
    val pendingOutput = outputsChannel.tryReceive().getOrNull()
    if (pendingOutput != null) {
      return processOutputFromChannel(pendingOutput)
    }

    return ActionsExhausted
  }

  @Composable
  private inline fun <ChildPropsT, ChildOutputT, ChildRenderingT> rememberComposeChildNode(
    childWorkflow: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    childIdentifier: WorkflowIdentifier,
    initialProps: ChildPropsT,
    noinline onOutput: ((ChildOutputT) -> Unit)?
  ): ComposeChildNode<ChildPropsT, ChildOutputT, ChildRenderingT> {
    val childRenderKey = rememberChildRenderKey()
    val childCoroutineScope = rememberCoroutineScope()
    val updatedOnOutput by rememberUpdatedState(onOutput)

    // Don't need to key the remember on workflow since we're already keyed on its identifier, which
    // also implies the workflow's type.
    return if (childWorkflow is ComposeWorkflow) {
      remember {
        val childId = WorkflowNodeId(childIdentifier, name = childRenderKey)
        val childSnapshot = snapshotCache?.get(childId)
        workflowTracer.trace("ComposeChildInstantiateComposeChildNode") {
          ComposeWorkflowChildNode(
            id = childId,
            initialProps = initialProps,
            snapshot = childSnapshot,
            baseContext = childCoroutineScope.coroutineContext,
            parentNode = this,
            parent = this,
            workflowTracer = workflowTracer,
            runtimeConfig = runtimeConfig,
            interceptor = interceptor,
            idCounter = idCounter,
            emitAppliedActionToParent = { actionApplied ->
              handleChildOutput(actionApplied, updatedOnOutput)
            }
          )
        }
      }
    } else {
      // We need to be able to explicitly request recomposition of this composable when an action
      // cascade results in this child changing state because traditional workflows don't know
      // about Compose. See comment in acceptChildActionResult for more info.
      val recomposeScope = currentRecomposeScope
      remember {
        val childId = WorkflowNodeId(childIdentifier, name = childRenderKey)
        val childSnapshot = snapshotCache?.get(childId)
        workflowTracer.trace("ComposeChildInstantiateTraditionalChildNode") {
          TraditionalWorkflowAdapterChildNode(
            id = childId,
            workflow = childWorkflow,
            initialProps = initialProps,
            contextForChildren = childCoroutineScope.coroutineContext,
            parentNode = this,
            parent = this,
            snapshot = childSnapshot,
            workflowTracer = workflowTracer,
            runtimeConfig = runtimeConfig,
            interceptor = interceptor,
            idCounter = idCounter,
            acceptChildActionResult = { actionApplied ->
              // If this child needs to be re-rendered on the next render pass and there are no other
              // state changes in the compose runtime during this action cascade, if we don't
              // explicitly invalidate the recompose scope then the recomposer will think it doesn't
              // have anything to do and not recompose us, which means we wouldn't have a chance to
              // re-render the traditional workflow.
              if (actionApplied.stateChanged) {
                recomposeScope.invalidate()
              }
              handleChildOutput(actionApplied, updatedOnOutput)
            }
          )
        }
      }
    }
  }

  fun addChildNode(childNode: ComposeChildNode<*, *, *>) {
    (childNodes ?: MutableVector<ComposeChildNode<*, *, *>>().also { childNodes = it }) += childNode
  }

  fun removeChildNode(childNode: ComposeChildNode<*, *, *>) {
    val childNodes = childNodes
      ?: throw AssertionError("removeChildNode called before addChildNode")
    childNodes.remove(childNode)
  }

  private fun createChildSnapshots(): Map<WorkflowNodeId, TreeSnapshot> = buildMap {
    childNodes?.forEach { child ->
      put(child.id, child.snapshot())
    }
  }

  /**
   * This is the lambda passed to every invocation of the [ComposeWorkflow.produceRendering] method.
   * It merely enqueues the output in the channel. The actual processing happens when the receiver
   * registered by [onNextAction] calls [processOutputFromChannel].
   */
  private fun sendOutputToChannel(output: OutputT) {
    // TODO defer this work until some time very soon in the future, but after the immediate caller
    //  has returned. E.g. launch a coroutine, but make sure it runs before the next frame (compose
    //  or choreographer). This will ensure that if the caller sets state _after_ calling this
    //  method the state changes are consumed by the resulting recomposition.

    // If dispatcher is Main.immediate this will synchronously perform re-render.
    println("sending output to channel: $output")
    outputsChannel.requireSend(output)
  }

  private fun <ChildOutputT> handleChildOutput(
    appliedActionFromChild: ActionApplied<ChildOutputT>,
    onOutput: ((ChildOutputT) -> Unit)?
  ): ActionProcessingResult {
    workflowTracer.trace("ComposeChildHandleChildOutput") {
      log("handling child output: $appliedActionFromChild")
      val outputFromChild = appliedActionFromChild.output
      // The child emitted an output, so we need to call our handler, which will zero or
      // more of two things: (1) change our state, (2) emit an output.

      // If this workflow calls emitOutput while running child output handler, we don't want
      // to send it to the channel, but rather capture it and propagate to our parent
      // directly. But only for the first call to emitOutput – subsequent calls will need to
      // be handled as usual.
      var maybeParentResult: ActionProcessingResult? = null

      if (outputFromChild != null && onOutput != null) {
        onEmitOutputOverride = { output ->
          // We can't know if our own state changed, so just propagate from the child.
          val applied = appliedActionFromChild.withOutput(WorkflowOutput(output))
          log("handler emitted output, propagating to parent…")
          maybeParentResult = emitAppliedActionToParent(applied)

          // Immediately allow any future emissions in the same onOutput call to pass through.
          onEmitOutputOverride = null
        }
        // Ask this workflow to handle the child's output. It may write snapshot state or call
        // emitOutput.
        onOutput(outputFromChild.value)
        onEmitOutputOverride = null
      }

      if (maybeParentResult == null) {
        // onOutput did not call emitOutput, but we need to propagate the action cascade anyway to
        // check if state changed.
        log("handler did not emitOutput, propagating to parent anyway…")
        maybeParentResult = emitAppliedActionToParent(appliedActionFromChild.withOutput(null))
      }

      // If maybeParentResult is not null then onOutput called emitOutput.
      return maybeParentResult ?: appliedActionFromChild.withOutput(null)
    }
  }

  private fun ActionApplied<*>.withOutput(output: WorkflowOutput<OutputT>?) =
    ActionApplied(output = output, stateChanged = stateChanged)
}
