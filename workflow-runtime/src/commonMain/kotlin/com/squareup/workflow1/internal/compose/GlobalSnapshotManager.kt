package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// TODO make this thread safe
internal object GlobalSnapshotManager {
  private var started = false
  private var applyScheduled = false

  fun ensureStarted() {
    if (started) return
    started = true
    Snapshot.registerGlobalWriteObserver {
      if (!applyScheduled) {
        applyScheduled = true
        CoroutineScope(GlobalSnapshotCoroutineDispatcher).launch {
          applyScheduled = false
          Snapshot.sendApplyNotifications()
        }
      }
    }
  }
}

internal expect val GlobalSnapshotCoroutineDispatcher: CoroutineDispatcher
