package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderingProduced
import com.squareup.workflow1.WorkflowInterceptor.RuntimeSettled
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.compose.TraceLabels.PerformSave
import com.squareup.workflow1.internal.compose.TraceLabels.Recompose
import com.squareup.workflow1.internal.compose.runtime.launchSynchronizedMolecule
import com.squareup.workflow1.internal.requireSend
import com.squareup.workflow1.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * This is the entry point into the entire Compose-based workflow runtime. It owns the Compose
 * runtime that manages the workflow tree.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
internal fun <PropsT, OutputT, RenderingT> renderWorkflowWithComposeRuntimeIn(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  scope: CoroutineScope,
  props: StateFlow<PropsT>,
  initialSnapshot: TreeSnapshot? = null,
  interceptor: WorkflowInterceptor,
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  workflowTracer: WorkflowTracer? = null,
  onOutput: suspend (OutputT) -> Unit,
): StateFlow<RenderingAndSnapshot<RenderingT>> {
  val outputs = Channel<OutputT>(capacity = 1000)
  val recomposeRequests = Channel<Unit>(capacity = 1)
  val composableConfig =
    WorkflowComposableRuntimeConfig(
      workflowInterceptor = interceptor,
      runtimeConfig = runtimeConfig,
      workflowTracer = workflowTracer,
      idCounter = IdCounter(),
    )
  val saveableStateRegistry = createSaveableStateRegistryForTreeSnapshot(initialSnapshot)

  // Explicitly store this lambda in a val so it doesn't get re-allocated every time, causing
  // recomposeWithContent to recompose unnecessarily.
  val molecule =
    scope.launchSynchronizedMolecule(onNeedsRecomposition = { recomposeRequests.trySend(Unit) }) {
      val currentProps by props.collectAsState()
      withCompositionLocals(LocalSaveableStateRegistry provides saveableStateRegistry) {
        renderWorkflow(
          workflow = workflow,
          props = currentProps,
          onOutput = outputs::requireSend,
          config = composableConfig,
          parentSession = null,
          renderKey = "",
        )
      }
    }

  fun recomposeAndTakeSnapshot(): RenderingAndSnapshot<RenderingT> {
    var rendering: RenderingT
    workflowTracer.trace(Recompose) {
      rendering = molecule.recompose()
      // I think this can only happen on the initial compose – otherwise we've got a backwards write
      // or something.
      while (recomposeRequests.tryReceive().isSuccess) {
        rendering = molecule.recompose()
      }
    }

    // Must perform the save eagerly so that the *current* state values are captured, instead of the
    // future values when the snapshot is actually serialized. This is less efficient, but matches
    // the behavior of the traditional workflow runtime.
    val savedValues = workflowTracer.trace(PerformSave) { saveableStateRegistry.performSave() }
    val snapshot = savedValuesToSnapshot(savedValues)
    val treeSnapshot = TreeSnapshot(snapshot, childTreeSnapshots = ::emptyMap)

    return RenderingAndSnapshot(rendering, snapshot = treeSnapshot)
  }

  val initialRenderingAndSnapshot = recomposeAndTakeSnapshot()
  val renderingsAndSnapshots = MutableStateFlow(initialRenderingAndSnapshot)

  interceptor.onRuntimeUpdate(RenderingProduced)
  interceptor.onRuntimeUpdate(RuntimeSettled)

  scope.launch {
    while (true) {
      // TODO it saves time to not use channels, but that was done without handling outputs so not
      //  sure if the gains would persist if we added that functionality back in.
      // awaitRecomposeRequest()
      // renderingsAndSnapshots.value = molecule.recompose()
      select<Unit> {
        // Must receive from outputs first so the outputs channel will be fully drained before
        // allowing recomposition to continue.
        outputs.onReceive { output ->
          val maybeRecomposeRequest = recomposeRequests.tryReceive()
          if (maybeRecomposeRequest.isSuccess) {
            // We need to publish the new rendering before sending any outputs, but we need to drain
            // the outputs queue before recomposing to maintain ordering.
            val outputsToSend = mutableListOf(output)
            var maybeOutput = outputs.tryReceive()
            while (maybeOutput.isSuccess) {
              outputsToSend += maybeOutput.getOrThrow()
              maybeOutput = outputs.tryReceive()
            }

            // First send the new rendering, to comply with workflow expectations.
            renderingsAndSnapshots.value = recomposeAndTakeSnapshot()
            interceptor.onRuntimeUpdate(RenderingProduced)
            interceptor.onRuntimeUpdate(RuntimeSettled)

            // Then send all the outputs that happened before recomposition.
            outputsToSend.forEach { onOutput(it) }
          } else {
            onOutput(output)
          }
        }
        recomposeRequests.onReceive {
          renderingsAndSnapshots.value = recomposeAndTakeSnapshot()
          interceptor.onRuntimeUpdate(RenderingProduced)
          interceptor.onRuntimeUpdate(RuntimeSettled)
        }
      }
    }
  }

  return renderingsAndSnapshots
}
