package com.squareup.workflow1.internal.compose.runtime

import androidx.compose.runtime.snapshots.Snapshot
import com.squareup.workflow1.internal.Lock
import com.squareup.workflow1.internal.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * When running benchmarks, using the Main dispatcher for sending apply notifications creates a race
 * condition where the main thread is in the middle of sending apply notifications so the benchmark
 * thread thinks they're sent and continues, which means sometimes recomposition isn't scheduled
 * until after the test scheduler is finished advancing, which kind of means the frame is missed.
 */
public fun setGlobalSnapshotManagerSendApplyImmediately(value: Boolean) {
  GlobalSnapshotManager.setSendApplyImmediately(value)
}


internal object GlobalSnapshotManager {
  private var started = false
  private var applyScheduled = false
  @Volatile private var sendApplyImmediately = false
  private val lock = Lock()

  fun setSendApplyImmediately(value: Boolean) {
    sendApplyImmediately = value
  }

  fun ensureStarted() {
    lock.withLock {
      if (started) return
      started = true
    }

    Snapshot.registerGlobalWriteObserver {
      val launchApply = lock.withLock {
        if (!applyScheduled) {
          applyScheduled = true
          true
        } else {
          false
        }
      }
      if (launchApply) {
        if (sendApplyImmediately) {
          lock.withLock {
            applyScheduled = false
          }
          Snapshot.sendApplyNotifications()
        } else {
          CoroutineScope(GlobalSnapshotCoroutineDispatcher).launch {
            lock.withLock {
              applyScheduled = false
            }
            Snapshot.sendApplyNotifications()
          }
        }
      }
    }
  }
}

internal expect val GlobalSnapshotCoroutineDispatcher: CoroutineDispatcher
