package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collection.mutableVectorOf
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
import com.squareup.workflow1.workflowSessionToString
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

@OptIn(WorkflowExperimentalApi::class)
internal class ComposeWorkflowChildNode<PropsT, OutputT, RenderingT>(
  override val id: WorkflowNodeId,
  initialProps: PropsT,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
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
  CoroutineScope {

  // We don't need to create our own job because unlike for WorkflowNode, the baseContext already
  // has a dedicated job: either from the adapter (for root compose workflow), or from
  // rememberCoroutineScope().
  override val coroutineContext: CoroutineContext = baseContext +
    CoroutineName(id.toString())

  // WorkflowSession properties
  override val identifier: WorkflowIdentifier get() = id.identifier
  override val renderKey: String get() = id.name
  override val sessionId: Long = idCounter.createId()

  private var lastProps by mutableStateOf(initialProps)
  private val saveableStateRegistry: SaveableStateRegistry
  private var snapshotCache = snapshot?.childTreeSnapshots
  private val nodesToSnapshot = mutableVectorOf<ComposeChildNode<*, *, *>>()

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

  /**
   * Function invoked when [onNextAction] receives an output from [outputsChannel].
   */
  private val processOutputFromChannel: (OutputT) -> ActionProcessingResult = { output ->
    // Ensure any state updates performed by the output sender gets to invalidate any
    // compositions that read them, so we can check hasInvalidations below.
    // If no frame has been requested yet this will request a frame. If the dispatcher is
    // Main.immediate the frame will be requested synchronously.
    Snapshot.sendApplyNotifications()

    val applied = ActionApplied(
      output = WorkflowOutput(output),
      // We can't know if any state read by this workflow composable specifically changed, but the
      // ComposeWorkflowNodeAdapter hosting the composition will modify this as necessary based on
      // whether _any_ state in the composition changed.
      stateChanged = false
    )

    // Invoke the parent's handler to propagate the output up the workflow tree.
    emitAppliedActionToParent(applied)
  }

  init {
    interceptor.onSessionStarted(workflowScope = this, session = this)
    val workflowSnapshot = snapshot?.workflowSnapshot
    var restoredRegistry: SaveableStateRegistry? = null
    // Don't care about this return value, our state is separate.
    interceptor.onInitialState(
      props = initialProps,
      snapshot = workflowSnapshot,
      workflowScope = this,
      session = this,
      proceed = { innerProps, innerSnapshot, _ ->
        lastProps = innerProps
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
    workflow as ComposeWorkflow
    return withCompositionLocals(
      LocalSaveableStateRegistry provides saveableStateRegistry,
      LocalWorkflowComposableRenderer provides this
    ) {
      interceptor.onRenderComposeWorkflow(
        renderProps = lastProps,
        emitOutput = onEmitOutput,
        session = this,
        proceed = { innerProps, innerEmitOutput ->
          workflow.produceRendering(
            props = innerProps,
            emitOutput = innerEmitOutput
          )
        }
      )
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
      val childNode = rememberComposeChildNode(
        childWorkflow = childWorkflow,
        childIdentifier = childIdentifier,
        initialProps = props,
        onOutput = onOutput
      )

      // Track child nodes for snapshotting.
      // NOTE: While the effect will run after composition, it will run as part of the compose
      // frame, so the child will be registered before ComposeWorkflowNodeAdapter's render method
      // returns.
      DisposableEffect(Unit) {
        addChildNode(childNode)
        onDispose {
          removeChildNode(childNode)
        }
      }

      return@key childNode.produceRendering(childWorkflow, props)
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

  @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
  override fun onNextAction(selector: SelectBuilder<ActionProcessingResult>): Boolean {
    val empty = outputsChannel.isEmpty || outputsChannel.isClosedForReceive
    with(selector) {
      outputsChannel.onReceive(processOutputFromChannel)
    }
    return empty
  }

  @Composable
  private fun <ChildPropsT, ChildOutputT, ChildRenderingT> rememberComposeChildNode(
    childWorkflow: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    childIdentifier: WorkflowIdentifier,
    initialProps: ChildPropsT,
    onOutput: ((ChildOutputT) -> Unit)?
  ): ComposeChildNode<ChildPropsT, ChildOutputT, ChildRenderingT> {
    val childRenderKey = rememberChildRenderKey()
    val childId = WorkflowNodeId(childIdentifier, name = childRenderKey)
    val childSnapshot = snapshotCache?.get(childId)
    val childCoroutineScope = rememberCoroutineScope()
    val updatedOnOutput by rememberUpdatedState(onOutput)

    // Don't need to key the remember on workflow since we're already keyed on its identifier, which
    // also implies the workflow's type.
    return if (childWorkflow is ComposeWorkflow) {
      remember {
        ComposeWorkflowChildNode(
          id = childId,
          initialProps = initialProps,
          snapshot = childSnapshot,
          baseContext = childCoroutineScope.coroutineContext,
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
    } else {
      // We need to be able to explicitly request recomposition of this composable when an action
      // cascade results in this child changing state because traditional workflows don't know
      // about Compose.
      val recomposeScope = currentRecomposeScope
      remember {
        TraditionalWorkflowAdapterChildNode(
          id = childId,
          workflow = childWorkflow,
          initialProps = initialProps,
          contextForChildren = childCoroutineScope.coroutineContext,
          parent = this,
          snapshot = childSnapshot,
          workflowTracer = workflowTracer,
          runtimeConfig = runtimeConfig,
          interceptor = interceptor,
          idCounter = idCounter,
          acceptChildActionResult = { actionApplied ->
            if (actionApplied.stateChanged) {
              recomposeScope.invalidate()
            }
            handleChildOutput(actionApplied, updatedOnOutput)
          }
        )
      }
    }
  }

  private fun addChildNode(childNode: ComposeChildNode<*, *, *>) {
    nodesToSnapshot += childNode
  }

  private fun removeChildNode(childNode: ComposeChildNode<*, *, *>) {
    nodesToSnapshot -= childNode
  }

  private fun createChildSnapshots(): Map<WorkflowNodeId, TreeSnapshot> = buildMap {
    nodesToSnapshot.forEach { child ->
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
    val outputFromChild = appliedActionFromChild.output
    if (outputFromChild == null || onOutput == null) {
      // The child didn't actually emit anything or we don't care, so we don't need to
      // propagate anything to the parent. We halt the action cascade by simply returning
      // here without calling emitAppliedActionToParent.
      //
      // NOTE: SubtreeManager has an additional case for PARTIAL_TREE_RENDERING, but we
      // can just assume that using ComposeWorkflow at all implies that optimization.
      //
      // If our child state changed, we need to report that ours did too, as per the
      // comment in StatefulWorkflowNode.applyAction.
      return appliedActionFromChild.withOutput(null)
    }

    // The child DID emit an output, so we need to call our handler, which will zero or
    // more of two things: (1) change our state, (2) emit an output.

    // If this workflow calls emitOutput while running child output handler, we don't want
    // to send it to the channel, but rather capture it and propagate to our parent
    // directly. But only for the first call to emitOutput – subsequent calls will need to
    // be handled as usual.
    var maybeParentResult: ActionProcessingResult? = null
    onEmitOutputOverride = { output ->
      // We can't know if our own state changed, so just propagate from the child.
      val applied = appliedActionFromChild.withOutput(WorkflowOutput(output))
      maybeParentResult = emitAppliedActionToParent(applied)

      // Immediately allow any future emissions in the same onOutput call to pass through.
      onEmitOutputOverride = null
    }
    // Ask this workflow to handle the child's output. It may write snapshot state or call
    // emitOutput.
    onOutput(outputFromChild.value)
    onEmitOutputOverride = null

    // If maybeParentResult is not null then onOutput called emitOutput.
    return maybeParentResult ?: appliedActionFromChild.withOutput(null)
  }

  private fun ActionApplied<*>.withOutput(output: WorkflowOutput<OutputT>?) =
    ActionApplied(output = output, stateChanged = stateChanged)
}
