package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.internal.compose.runtime.SynchronizedMolecule
import com.squareup.workflow1.internal.compose.runtime.launchSynchronizedMolecule
import com.squareup.workflow1.internal.compose.runtime.setGlobalSnapshotManagerSendApplyImmediately
import kotlinx.coroutines.CoroutineScope

/**
 * Test harness that hosts a [SynchronizedMolecule] and applies snapshot writes immediately so
 * tests don't have to coordinate with a real frame clock dispatcher. Use [recompose] to run
 * a composable; if any state read inside the composable was changed since the previous call,
 * Compose will recompose the affected scopes before returning.
 *
 * The global "send apply immediately" flag is enabled (via [enableImmediateApplyForTests]) but
 * never turned off, because [GlobalSnapshotManager]'s registered global write observer will
 * otherwise try to dispatch to `Dispatchers.Main`, which isn't installed in plain JVM unit tests.
 *
 * Tests should call [close] in a `finally` (or via a deferred cleanup) to dispose the underlying
 * recomposer.
 */
internal class TestComposition(scope: CoroutineScope) {
  private var recomposeRequests: Int = 0
  private val molecule: SynchronizedMolecule = run {
    enableImmediateApplyForTests()
    scope.launchSynchronizedMolecule(onNeedsRecomposition = { recomposeRequests++ })
  }

  /** Number of times the molecule has signaled that recomposition is needed. */
  val recomposeRequestCount: Int get() = recomposeRequests

  /** Mirrors [SynchronizedMolecule.needsRecomposition]. */
  val needsRecomposition: Boolean get() = molecule.needsRecomposition

  fun <R> recompose(content: @Composable () -> R): R = molecule.recomposeWithContent(content)

  fun close() {
    molecule.close()
  }
}

/**
 * Ensures [GlobalSnapshotManager]'s global write observer dispatches its
 * `Snapshot.sendApplyNotifications()` call synchronously instead of trying to use
 * `Dispatchers.Main`. Once enabled, it stays enabled for the rest of the test JVM.
 *
 * Idempotent — safe to call from any test, including ones that don't use [TestComposition].
 */
internal fun enableImmediateApplyForTests() {
  setGlobalSnapshotManagerSendApplyImmediately(true)
}
