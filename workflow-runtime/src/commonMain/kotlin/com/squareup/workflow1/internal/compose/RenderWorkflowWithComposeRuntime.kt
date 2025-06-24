package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderingProduced
import com.squareup.workflow1.WorkflowInterceptor.RuntimeSettled
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.compose.TraceLabels.PerformSave
import com.squareup.workflow1.internal.compose.TraceLabels.Recompose
import com.squareup.workflow1.internal.compose.runtime.SynchronizedMolecule
import com.squareup.workflow1.internal.compose.runtime.launchSynchronizedMolecule
import com.squareup.workflow1.internal.requireSend
import com.squareup.workflow1.parse
import com.squareup.workflow1.readByteStringWithLength
import com.squareup.workflow1.readList
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.trace
import com.squareup.workflow1.writeByteStringWithLength
import com.squareup.workflow1.writeList
import com.squareup.workflow1.writeUtf8WithLength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import okio.ByteString

@OptIn(ExperimentalComposeRuntimeApi::class)
internal fun <PropsT, OutputT, RenderingT> renderWorkflowWithComposeRuntimeIn(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  scope: CoroutineScope,
  props: StateFlow<PropsT>,
  initialSnapshot: TreeSnapshot? = null,
  interceptor: WorkflowInterceptor,
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  workflowTracer: WorkflowTracer? = null,
  onOutput: suspend (OutputT) -> Unit
): StateFlow<RenderingAndSnapshot<RenderingT>> {
  val outputs = Channel<OutputT>(capacity = 1000)
  val recomposeRequests = Channel<Unit>(capacity = 1)
  val composableConfig = WorkflowComposableRuntimeConfig(
    workflowInterceptor = interceptor,
    runtimeConfig = runtimeConfig,
    workflowTracer = workflowTracer,
    idCounter = IdCounter()
  )
  val saveableStateRegistry = createSaveableStateRegistryForTreeSnapshot(initialSnapshot)

  // Explicitly store this lambda in a val so it doesn't get re-allocated every time, causing
  // recomposeWithContent to recompose unnecessarily.
  val content: @Composable () -> RenderingT = {
    val currentProps by props.collectAsState()
    withCompositionLocals(LocalSaveableStateRegistry provides saveableStateRegistry) {
      renderWorkflow(
        workflow = workflow,
        props = currentProps,
        onOutput = outputs::requireSend,
        config = composableConfig,
        parentSession = null,
        renderKey = ""
      )
    }
  }

  fun SynchronizedMolecule.recompose(): RenderingAndSnapshot<RenderingT> {
    var rendering: RenderingT
    workflowTracer.trace(Recompose) {
      rendering = recomposeWithContent(content)
      // I think this can only happen on the initial compose – otherwise we've got a backwards write
      // or something.
      while (recomposeRequests.tryReceive().isSuccess) {
        rendering = recomposeWithContent(content)
      }
    }

    // Must perform the save eagerly so that the *current* state values are captured, instead of the
    // future values when the snapshot is actually serialized. This is less efficient, but matches
    // the behavior of the traditional workflow runtime.
    val savedValues = workflowTracer.trace(PerformSave) {
      saveableStateRegistry.performSave()
    }
    val snapshot = savedValuesToSnapshot(savedValues)
    val treeSnapshot = TreeSnapshot(snapshot, childTreeSnapshots = ::emptyMap)

    return RenderingAndSnapshot(rendering, snapshot = treeSnapshot)
  }

  val molecule = scope.launchSynchronizedMolecule(
    onNeedsRecomposition = { recomposeRequests.trySend(Unit) }
  )
  val initialRenderingAndSnapshot = molecule.recompose()
  val renderingsAndSnapshots = MutableStateFlow(initialRenderingAndSnapshot)

  interceptor.onRuntimeUpdate(RenderingProduced)
  interceptor.onRuntimeUpdate(RuntimeSettled)

  scope.launch {
    while (true) {
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
            renderingsAndSnapshots.value = molecule.recompose()
            interceptor.onRuntimeUpdate(RenderingProduced)
            interceptor.onRuntimeUpdate(RuntimeSettled)

            // Then send all the outputs that happened before recomposition.
            outputsToSend.forEach {
              onOutput(it)
            }
          } else {
            onOutput(output)
          }
        }
        recomposeRequests.onReceive {
          renderingsAndSnapshots.value = molecule.recompose()
          interceptor.onRuntimeUpdate(RenderingProduced)
          interceptor.onRuntimeUpdate(RuntimeSettled)
        }
      }
    }
  }

  return renderingsAndSnapshots
}

private fun createSaveableStateRegistryForTreeSnapshot(
  treeSnapshot: TreeSnapshot?
): SaveableStateRegistry {
  val snapshot = treeSnapshot?.workflowSnapshot
  val restoredValues = snapshotToRestoredValues(snapshot)
  return SaveableStateRegistry(
    restoredValues = restoredValues,
    canBeSaved = { it is Snapshot || (it is MutableState<*> && it.value is Snapshot) }
  )
}

private fun snapshotToRestoredValues(snapshot: Snapshot?): Map<String, List<Any?>>? {
  if (snapshot == null) return null
  return buildMap {
    snapshot.bytes.parse { source ->
      val mapSize = source.readInt()
      repeat(mapSize) {
        val key = source.readUtf8WithLength()
        val snapshots: List<Any?> = source.readList {
          when (val valueTypeTag = source.readByte()) {
            0.toByte() -> {
              // Direct snapshot.
              val bytes = source.readByteStringWithLength()
              if (bytes.size == 0) null else Snapshot.of(bytes)
            }

            1.toByte() -> {
              // MutableState of snapshot.
              val bytes = source.readByteStringWithLength()
              val snapshot = if (bytes.size == 0) null else Snapshot.of(bytes)
              snapshot?.let(::mutableStateOf)
            }

            else -> error("Unknown tag: $valueTypeTag")
          }
        }
        if (snapshots.isNotEmpty()) {
          put(key, snapshots)
        }
      }
    }
  }
}

private fun savedValuesToSnapshot(savedValues: Map<String, List<Any?>>): Snapshot =
  Snapshot.write { sink ->
    sink.writeInt(savedValues.size)
    savedValues.entries.forEach { (key, snapshots) ->
      sink.writeUtf8WithLength(key)
      sink.writeList(snapshots) {
        when (it) {
          is Snapshot? -> {
            sink.writeByte(0)
            val snapshot = it
            val bytes = snapshot?.bytes ?: ByteString.EMPTY
            sink.writeByteStringWithLength(bytes)
          }

          is MutableState<*> -> {
            sink.writeByte(1)
            val snapshot = it.value as Snapshot?
            val bytes = snapshot?.bytes ?: ByteString.EMPTY
            sink.writeByteStringWithLength(bytes)
          }

          else -> error(
            "Expected saved state value to be a Snapshot or MutableState<Snapshot>, " +
              "but was $it"
          )
        }

      }
    }
  }
