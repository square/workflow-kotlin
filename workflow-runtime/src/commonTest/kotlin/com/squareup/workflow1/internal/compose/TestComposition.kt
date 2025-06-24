package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.molecule.RecompositionMode
import app.cash.molecule.SnapshotNotifier
import app.cash.molecule.launchMolecule
import com.squareup.workflow1.internal.WorkStealingDispatcher
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus

/**
 * Test harness that hosts a [SynchronizedMolecule] and applies snapshot writes immediately so tests
 * don't have to coordinate with a real frame clock dispatcher. Use [recompose] to run a composable;
 * if any state read inside the composable was changed since the previous call, Compose will
 * recompose the affected scopes before returning.
 *
 * The global "send apply immediately" flag is enabled (via [enableImmediateApplyForTests]) but
 * never turned off, because [GlobalSnapshotManager]'s registered global write observer will
 * otherwise try to dispatch to `Dispatchers.Main`, which isn't installed in plain JVM unit tests.
 *
 * Tests should call [close] in a `finally` (or via a deferred cleanup) to dispose the underlying
 * recomposer.
 */
internal class TestComposition<R>(scope: CoroutineScope, content: @Composable () -> R) {
  private val clock =
    BroadcastFrameClock(
      onNewAwaiters = {
        needsRecomposition = true
        recomposeRequestCount++
      }
    )
  private val dispatcher =
    WorkStealingDispatcher(
      scope.coroutineContext[ContinuationInterceptor] ?: Dispatchers.Unconfined
    )
  private val scope = scope + Job(parent = scope.coroutineContext[Job]) + dispatcher + clock
  private val renderings =
    this.scope.launchMolecule(
      mode = RecompositionMode.ContextClock,
      snapshotNotifier = SnapshotNotifier.WhileActive,
      body = content,
    )

  /** Number of times the molecule has signaled that recomposition is needed. */
  var recomposeRequestCount: Int = 0
    private set

  var needsRecomposition: Boolean = false
    private set

  fun recompose(): R {
    Snapshot.sendApplyNotifications()
    dispatcher.advanceUntilIdle()
    clock.sendFrame(0L)
    needsRecomposition = false
    return renderings.value
  }

  fun close() {
    scope.cancel()
  }
}
