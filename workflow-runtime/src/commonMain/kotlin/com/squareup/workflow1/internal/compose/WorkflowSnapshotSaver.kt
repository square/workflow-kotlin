package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.trace
import kotlinx.coroutines.CoroutineScope

/**
 * Uses [statefulWorkflow]'s [StatefulWorkflow.snapshotState] and [StatefulWorkflow.initialState]
 * methods to save and restore its state to a [ByteArray] that can be stored in a bundle or
 * serialized inside [renderChild].
 */
internal class WorkflowSnapshotSaver<PropsT, StateT>(
  private val initialProps: PropsT,
  private val statefulWorkflow: StatefulWorkflow<PropsT, StateT, *, *>,
  private val workflowTracer: WorkflowTracer?,
  private val workflowScope: CoroutineScope,
) : Saver<StateT, Snapshot> {

  @OptIn(WorkflowExperimentalApi::class)
  override fun restore(value: Snapshot): StateT? =
    workflowTracer.trace(TraceLabels.InitialState) {
      statefulWorkflow.initialState(
        props = initialProps,
        snapshot = value,
        workflowScope = workflowScope,
      )
    }

  override fun SaverScope.save(value: StateT): Snapshot? =
    statefulWorkflow.snapshotState(value)
}
