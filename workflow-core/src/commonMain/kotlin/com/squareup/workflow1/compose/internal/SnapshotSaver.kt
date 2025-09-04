package com.squareup.workflow1.compose.internal

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.compose.renderChild
import okio.ByteString

/**
 * Uses [statefulWorkflow]'s [StatefulWorkflow.snapshotState] and [StatefulWorkflow.initialState]
 * methods to save and restore its state to a [ByteArray] that can be stored in a bundle or
 * serialized inside [renderChild].
 */
internal class SnapshotSaver<PropsT, StateT>(
  private val initialProps: PropsT,
  private val statefulWorkflow: StatefulWorkflow<PropsT, StateT, *, *>
) : Saver<StateT, ByteArray> {
  override fun restore(value: ByteArray): StateT? = statefulWorkflow.initialState(
    props = initialProps,
    snapshot = Snapshot.of(ByteString.of(*value))
  )

  override fun SaverScope.save(value: StateT): ByteArray? =
    statefulWorkflow.snapshotState(value)?.bytes?.toByteArray()
}
