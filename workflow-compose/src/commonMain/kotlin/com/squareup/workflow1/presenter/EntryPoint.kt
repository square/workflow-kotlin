package com.squareup.workflow1.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.State
import androidx.compose.runtime.monotonicFrameClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The default [PresenterPolicy] used by [present] and [subcomposePresenter].
 */
val DefaultRootPresenterPolicy: PresenterPolicy = PresenterPolicy { children ->
  children.forEach { child ->
    // ViewModelRefs from later children will overwrite those from earlier children.
    child.readAllSlotsTo(this.outputSlots)
  }
}

/**
 * Launches a coroutine into [scope] recomposes [presenter] until canceled. The composable can emit
 * presenter nodes via the [NavigationPresenter] composables, and each node emitted by the root [presenter]
 * will be given to [rootPresenterPolicy] to aggregate. The default policy simply forwards all
 * slots from all children, with later-composed children's slots taking precedence.
 *
 * The [PresenterSlotMap] in the returned state can be used to access all the slots written
 * to by [rootPresenterPolicy].
 *
 * This is the main entry point into this runtime. However, the highest-touch APIs are
 * [PresenterPolicy] and [PresenterSlot].
 */
@OptIn(ExperimentalComposeApi::class)
fun present(
  scope: CoroutineScope,
  rootPresenterPolicy: PresenterPolicy = DefaultRootPresenterPolicy,
  presenter: @Composable () -> Unit
): State<PresenterSlotMap> {
  lateinit var rootNode: PresenterNode
  val output = mutableStateOf(MutablePresenterSlotMap())
  val onNodeChanged: (PresenterNode) -> Unit = PresenterNode::invalidate
  val snapshotStateObserver = SnapshotStateObserver(onChangedExecutor = { it() })

  fun reproduce() {
    rootNode.visitDirtyChildren { node ->
      if (node.dirty) {
        snapshotStateObserver.observeReads(
          scope = node,
          onValueChangedForScope = onNodeChanged,
        ) {
          node.runProducer()
        }
        node.commitToStates()
      }
    }
  }

  val parentFrameClock = scope.coroutineContext.monotonicFrameClock
  // This doesn't need to be a BroadcastFrameClock since the only consumer of it is the recompose
  // loop itself. We don't need to handle multiple concurrent withFrame calls.
  val compositionFrameClock = object : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
      parentFrameClock.withFrameNanos { frameTime ->
        // Run the recomposition, applier, and effects.
        val returnValue = onFrame(frameTime)

        Snapshot.withMutableSnapshot {
          // Run the view model producers.
          reproduce()

          val map = MutablePresenterSlotMap()
          rootNode.readAllSlotsTo(map)
          output.value = map
        }
        return@withFrameNanos returnValue
      }
  }
  val recomposer = Recomposer(effectCoroutineContext = scope.coroutineContext)
  val composition = PresenterComposition(
    parent = recomposer,
    rootProducer = rootPresenterPolicy,
  )
  rootNode = composition.rootNode

  scope.launch(compositionFrameClock) {
    recomposer.runRecomposeAndApplyChanges()
  }

  composition.setContent(presenter)

  return output
}
