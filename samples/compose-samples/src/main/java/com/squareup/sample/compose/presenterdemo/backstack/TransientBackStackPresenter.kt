package com.squareup.sample.compose.presenterdemo.backstack

import androidx.collection.ScatterMap
import androidx.collection.mutableScatterMapOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.MutableSnapshot
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.snapshots.StateRecord
import androidx.compose.runtime.snapshots.readable
import androidx.compose.runtime.snapshots.writable
import androidx.compose.ui.util.fastMapTo
import com.squareup.workflow1.presenter.Presenter
import com.squareup.workflow1.presenter.PresenterModifier
import com.squareup.workflow1.presenter.ViewModel
import com.squareup.workflow1.presenter.ViewModelProducer
import com.squareup.workflow1.presenter.ViewModelProducerScope
import com.squareup.workflow1.presenter.ViewModelRef
import com.squareup.workflow1.presenter.ViewModelResolver
import com.squareup.workflow1.presenter.resolve
import com.squareup.workflow1.presenter.simplify
import com.squareup.workflow1.presenter.ui.LocalViewModelResolver

/**
 * Emits a back stack that is managed by a [TransientBackStackController].
 *
 * Navigation state is owned by whatever snapshot state you want. Forward navigation is performed by
 * calling [TransientBackStackController.push] and modifying those states. Backwards navigation is
 * [TransientBackStackController.pop].
 *
 * This back stack implementation is "transient" because navigation state is fully contained in
 * snapshot state objects, so it is not serializable, and can't be saved to a
 * [androidx.compose.runtime.saveable.SaveableStateRegistry].
 *
 * Example:
 * ```
 * val controller = rememberTransientBackStackController()
 * val stage = remember { mutableIntStateOf(0) }
 *
 * TransientBackStackPresenter(controller) {
 *   Stage(
 *     stage,
 *     onAdvanceToNextStage = {
 *       controller.push { stage++ }
 *     }
 *   )
 * }
 * ```
 */
@Composable
fun TransientBackStackPresenter(
  controller: TransientBackStackController,
  modifier: PresenterModifier = PresenterModifier,
  content: @Composable () -> Unit
) {
  val saveableStateHolder = rememberSaveableStateHolder()
  Presenter(
    modifier = modifier,
    viewModelProducer = controller as TransientBackStackControllerImpl,
  ) {
    saveableStateHolder.SaveableStateProvider(
      key = controller.currentEntryKey,
      content = content
    )
  }
}

@Composable
fun rememberTransientBackStackController(
  initialEntryDescription: String? = null
): TransientBackStackController {
  val resolver = LocalViewModelResolver.current
  val saveableStateHolder = rememberSaveableStateHolder()
  return remember(resolver, saveableStateHolder) {
    TransientBackStackControllerImpl(
      initialEntryDescription = initialEntryDescription,
      viewModelResolver = resolver,
      saveableStateHolder = saveableStateHolder,
    )
  }
}

sealed interface TransientBackStackController {
  /**
   * Pushes a new entry onto the backstack by calling [stateMutator] to update your snapshot state.
   *
   * Any states written by [stateMutator] have their previous values stored in the entry, and when
   * [pop] is called from this entry those state values will be restored.
   */
  fun push(
    description: String? = null,
    stateMutator: () -> Unit
  )

  /**
   * Pops an entry off the backstack, restoring whatever state values were set by the `stateMutator`
   * in the corresponding call to [push].
   *
   * @return False if there were no entries to pop, else true.
   */
  fun pop(): Boolean
}

private class TransientBackStackControllerImpl(
  initialEntryDescription: String?,
  private val viewModelResolver: ViewModelResolver,
  private val saveableStateHolder: SaveableStateHolder,
) : TransientBackStackController, ViewModelProducer<BackStackScreen> {
  private data class Entry(
    val viewModel: ViewModel,
    val states: ScatterMap<StateObject, StateRecord>,
    val description: String?,
  )

  private val entries = SnapshotStateList<Entry>()
  private var lastSeenViewModel: ViewModel? by mutableStateOf(null)
  private var currentEntryDescription: String? by mutableStateOf(initialEntryDescription)
  val currentEntryKey: Any
    get() = entries.lastOrNull() ?: this

  override fun push(
    description: String?,
    stateMutator: () -> Unit
  ) {
    // This operation involves multiple state reads and writes, so wrap the whole thing in a
    // snapshot to ensure consistency. All state changes from `stateMutator` and the update to the
    // `entries` list will be applied atomically.
    Snapshot.withMutableSnapshot {
      val viewModel = checkNotNull(lastSeenViewModel) {
        "Cannot call push before first composition."
      }
      val currentEntrySnapshot = Snapshot.current as MutableSnapshot
      val states = mutableScatterMapOf<StateObject, StateRecord?>()
      val nextEntrySnapshot = currentEntrySnapshot.takeNestedMutableSnapshot(
        // Record all state objects that stateMutator writes to.
        writeObserver = { stateObject ->
          states.put(stateObject as StateObject, null)
        }
      )
      try {
        // Stage the state changes. After this call:
        //  - `snapshot` will "contain" the new values for all state objects, but they will not be
        //    visible anywhere yet.
        //  - `states` will have all the state objects that were written, but without values.
        nextEntrySnapshot.enter(stateMutator)

        // Now we record the "previous" values for the written state objects.
        states.forEachKey { stateObject ->
          val currentRecord =
            stateObject.firstStateRecord.readable(stateObject, currentEntrySnapshot)
          val duplicate = currentRecord.create()
          duplicate.assign(currentRecord)
          states.put(stateObject, duplicate)
        }

        // Make the new values "official".
        nextEntrySnapshot.apply().check()
      } finally {
        nextEntrySnapshot.dispose()
      }

      // Save the resolved view model, not the ref, so it's a true snapshot of the production at
      // this point in time. The state change may just result in the same ref producing a different
      // model.
      val resolvedViewModel = viewModelResolver.resolve(viewModel)

      @Suppress("UNCHECKED_CAST")
      entries += Entry(
        viewModel = resolvedViewModel,
        states = states as ScatterMap<StateObject, StateRecord>,
        description = currentEntryDescription,
      )
      currentEntryDescription = description
    }
  }

  override fun pop(): Boolean {
    // This operation involves multiple state reads and writes, so wrap the whole thing in a
    // snapshot to ensure consistency.
    val poppedEntry = Snapshot.withMutableSnapshot {
      val snapshot = Snapshot.current
      val poppedEntry = entries.removeLastOrNull() ?: return false

      // Restore saved values.
      poppedEntry.states.forEach { stateObject, stateRecord ->
        stateObject.firstStateRecord.writable(stateObject, snapshot) { assign(stateRecord) }
      }
      currentEntryDescription = poppedEntry.description
      lastSeenViewModel = poppedEntry.viewModel
      poppedEntry
    }

    saveableStateHolder.removeState(poppedEntry)
    return true
  }

  override fun ViewModelProducerScope.produce(children: List<ViewModelRef>): BackStackScreen {
    val child = children.simplify()
    lastSeenViewModel = child

    val entries = buildList {
      entries.fastMapTo(this) { it.viewModel }
      add(child)
    }

    return BackStackScreen(
      entries = entries,
      onBack = { pop() },
    )
  }
}
