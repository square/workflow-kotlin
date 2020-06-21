/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.squareup.workflow

import com.squareup.workflow.internal.SnapshotCache
import com.squareup.workflow.internal.createTreeSnapshot
import com.squareup.workflow.internal.parseTreeSnapshot
import okio.ByteString

/**
 * TODO kdoc
 */
class WorkflowSeed<PropsT, StateT> internal constructor(
  internal val initialProps: PropsT,
  internal val initialState: StateT,
  internal val snapshotCache: SnapshotCache,
  internal val restoredFromSnapshot: Boolean
) {
  companion object {
    /**
     * TODO kdoc
     */
    fun <PropsT, StateT> forTest(
      initialProps: PropsT,
      initialState: StateT
    ): WorkflowSeed<PropsT, StateT> =
      WorkflowSeed(
          initialProps, initialState, snapshotCache = emptyMap(), restoredFromSnapshot = false
      )

    /**
     * TODO kdoc
     */
    internal fun <PropsT, StateT> fromRootWorkflowSnapshot(
      workflow: StatefulWorkflow<PropsT, StateT, *, *>,
      initialProps: PropsT,
      snapshot: ByteString
    ): WorkflowSeed<PropsT, StateT> {
      // We need to wrap it in the rest of the envelope that the runtime expects so it looks like it
      // came out of the runtime.
      val treeSnapshot = createTreeSnapshot(snapshot, emptyList())
      return fromSnapshot(workflow, initialProps, treeSnapshot.bytes)
    }

    /**
     * TODO kdoc
     */
    internal fun <PropsT, StateT> fromSnapshot(
      workflow: StatefulWorkflow<PropsT, StateT, *, *>,
      initialProps: PropsT,
      snapshot: ByteString?
    ): WorkflowSeed<PropsT, StateT> {
      val (snapshotToRestoreFrom, snapshotCache) = snapshot?.takeUnless { it.size == 0 }
          .restoreState()
      val initialState = workflow.initialState(initialProps, snapshotToRestoreFrom)
      return WorkflowSeed(
          initialProps, initialState, snapshotCache,
          restoredFromSnapshot = snapshotToRestoreFrom != null
      )
    }

    private fun ByteString?.restoreState(): Pair<Snapshot?, SnapshotCache> {
      if (this == null) return Pair(null, emptyMap())
      val (snapshotToRestoreFrom, childSnapshots) = parseTreeSnapshot(this)
      return Pair(snapshotToRestoreFrom?.let { Snapshot.of(it) }, childSnapshots.toMap())
    }
  }
}
