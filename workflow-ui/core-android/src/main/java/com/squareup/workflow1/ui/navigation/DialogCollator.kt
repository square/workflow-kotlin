package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.navigation.DialogCollator.IdAndSessions
import com.squareup.workflow1.ui.navigation.DialogSession.KeyAndBundle
import java.util.UUID

/**
 * Helper provided to [DialogSessionUpdate.doUpdate] to give access
 * to an existing [DialogSession] able to display a given [Overlay].
 */
internal fun interface OldSessionFinder {
  /**
   * Returns the existing [DialogSession] that can be [updated][DialogSession.show]
   * to display [overlay], or `null` if there is none.
   */
  fun find(overlay: Overlay): DialogSession?
}

/**
 * Provided by [LayeredDialogSessions] to [DialogCollator.scheduleUpdates].
 * Knows how to create a [DialogSession] for [overlay], or to update an existing
 * one.
 *
 * @param overlay rendering to be shown in a new or existing Dialog window
 *
 * @param doUpdate function to create or update a [DialogSession] to display
 * [overlay]. Provided with an [OldSessionFinder] instance that gives access to the
 * existing session, if any, and a `covered: Boolean` param that indicates
 * if there is a [ModalOverlay] above [overlay]
 */
internal class DialogSessionUpdate(
  val overlay: Overlay,
  val doUpdate: (
    oldSessionFinder: OldSessionFinder,
    covered: Boolean
  ) -> DialogSession
) {
  override fun toString(): String {
    return "DialogSessionUpdate(overlay=${Compatible.keyFor(overlay)})"
  }
}

/**
 * Init method called at the start of [LayeredDialogSessions.update].
 * Ensures that there is a single [DialogCollator] instance shared by
 * an entire recursive hierarchy of [LayeredDialogSessions] for each
 * update pass.
 *
 * Each call to [establishDialogCollator] must be matched with a call to
 * [DialogCollator.scheduleUpdates]. This balance lets us know when a set of recursive
 * [LayeredDialogSessions.update] calls is complete, so that we can manage
 * the entire set of [Dialog][android.app.Dialog] windows.
 *
 * Returns a new [ViewEnvironment] with a [DialogCollator]
 * value if none was found, otherwise returns self. Either way,
 * the given [existingSessions] set is added to the pool
 * that will be processed by the new or existing [DialogCollator].
 *
 * @param id unique runtime-only id used to pair calls to [establishDialogCollator]
 * and [DialogCollator.scheduleUpdates]
 *
 * @param existingSessions [DialogSession] instances that were running before the pending update
 *
 * @param onRootUpdateFinished honored only for the outermost [LayeredDialogSessions] caller.
 * If provided, called with the complete set of [DialogSession] created by
 * [DialogCollator.scheduleUpdates], not just those of the client identified by [id].
 */
internal fun ViewEnvironment.establishDialogCollator(
  id: UUID,
  existingSessions: List<DialogSession>,
  onRootUpdateFinished: ((List<DialogSession>) -> Unit)?
): ViewEnvironment {
  val collatorOrNull = map[DialogCollator]
  val collator = (collatorOrNull as? DialogCollator) ?: DialogCollator()

  collator.establishedSessions.add(
    if (collator.expectedUpdates == 0) {
      IdAndSessions(id, existingSessions, onRootUpdateFinished)
    } else {
      IdAndSessions(id, existingSessions)
    }
  )
  collator.expectedUpdates++

  return if (collatorOrNull == null) this + (DialogCollator to collator) else this
}

/**
 * Singleton resource shared by a recursive set of [LayeredDialogSessions], used to
 * ensure that the windows they manage are stacked in the correct order and stay that
 * way across updates.
 *
 * Android notoriously doesn't give us any control over the z order of windows
 * other than taking care to [show][android.app.Dialog.show] them in the right
 * order, so keeping a pile of windows in sync with the shifting order of the
 * defining list of [Overlay] rendering models is hard. [DialogCollator] manages
 * that process.
 *
 * When a workflow UI tree is updated (that is, each time
 * [WorkflowLayout][com.squareup.workflow1.ui.WorkflowLayout.show] is called), a shared
 * [DialogCollator] is used across nested [LayeredDialogSessions] to coordinate their work.
 * Specifically, a [DialogCollator] instance is put in place in the [ViewEnvironment]
 * when [LayeredDialogSessions.update] calls [ViewEnvironment.establishDialogCollator].
 * The [LayeredDialogSessions] then loads its [DialogCollator] with functions to create or update
 * managed [Dialog][android.app.Dialog] instances, by calling [scheduleUpdates].
 *
 * Any recursive calls to [LayeredDialogSessions.update] receive the same [DialogCollator]
 * when they make their own calls to [ViewEnvironment.establishDialogCollator], and so
 * enqueue their updates in the same place.
 *
 * When control returns to the outermost [scheduleUpdates] stack frame, all of the
 * updates that were enqueued with the shared [DialogCollator] are executed in a single
 * pass. Because this [DialogCollator] has complete knowledge of the existing stack
 * of `Dialog` windows and all updates, it is able to decide if any existing instances need to be
 * [destroyed][DialogSession.destroyDialog] and rebuilt to keep them in the correct order.
 */
internal class DialogCollator {
  /**
   * Set of [DialogSession] instances registered by a specific [LayeredDialogSessions],
   * via [establishDialogCollator].
   */
  internal class IdAndSessions(
    val id: UUID,
    val sessions: List<DialogSession>,
    val onRootUpdateFinished: ((List<DialogSession>) -> Unit)? = null
  )

  /**
   * The [IdAndSessions] sets accumulated by all calls to [ViewEnvironment.establishDialogCollator].
   * Can be flattened to the current list of [DialogSession]s / `Dialog`s.
   */
  internal val establishedSessions = mutableListOf<IdAndSessions>()

  /**
   * The number of calls that have been made to [ViewEnvironment.establishDialogCollator].
   * Decremented when [scheduleUpdates] is called. When this returns to `0`,
   * [doUpdate] is called.
   */
  internal var expectedUpdates = 0

  /**
   * Set of [DialogSessionUpdate] functions registered by a specific [LayeredDialogSessions],
   * via [scheduleUpdates].
   */
  private class IdAndUpdates(
    val id: UUID,
    val updates: List<DialogSessionUpdate>,
    val onSessionsUpdated: (List<DialogSession>) -> Unit
  ) {
    override fun toString(): String {
      return "IdAndUpdates(id=$id, updates=$updates)"
    }
  }

  /**
   * The [IdAndUpdates] instances accumulated by all calls to [scheduleUpdates].
   * Can be flattened to the list of [Overlay] representing the
   * `Dialog` windows that will be in place when we're done updating.
   */
  private val allUpdates = mutableListOf<IdAndUpdates>()

  /**
   * Follow up call to [ViewEnvironment.establishDialogCollator]. Adds a set
   * of update operations to be applied to the [DialogSession] set provided
   * to that call. The updates are not applied until a matching call
   * to [scheduleUpdates] has been received for each call to [establishDialogCollator].
   *
   * @param id unique runtime-only id used to pair calls to [establishDialogCollator]
   * and [DialogCollator.scheduleUpdates]
   *
   * @param updates list of [DialogSessionUpdate] to be applied to the [DialogSession]
   * previously registered with [establishedSessions]. This is a list of
   * [Overlay]s to show, each paired with a function to create or update a
   * [DialogSession] to show it.
   *
   * @param onSessionsUpdated called immediately after the given [updates] are applied.
   * Provides the updated list of [DialogSession] that should replace those that
   * were provided to [establishDialogCollator].
   */
  internal fun scheduleUpdates(
    id: UUID,
    updates: List<DialogSessionUpdate>,
    onSessionsUpdated: (List<DialogSession>) -> Unit
  ) {
    check(expectedUpdates > 0) {
      "Each scheduleUpdates() call must be preceded by a call to" +
        " ViewEnvironment.establishDialogCollator, but expectedUpdates is $expectedUpdates"
    }

    // Under nested ComposeView instances we may get redundant updates from the
    // same caller. Just throw away the upstream ones.
    this.allUpdates.removeAll { it.id == id }
    this.allUpdates.add(IdAndUpdates(id, updates, onSessionsUpdated))
    if (--expectedUpdates == 0) doUpdate()
  }

  private fun doUpdate() {
    // Flatten establishedSessions into an Iterator across the existing sessions from
    // bottom to top, each paired with the id of the LayeredDialogSessions that registered it.
    val establishedSessionsIterator: Iterator<Pair<UUID, DialogSession>> =
      establishedSessions.asReversed()
        .asSequence()
        .flatMap { it.sessions.map { session -> Pair(it.id, session) } }
        .iterator()

    // Z index of the uppermost ModalOverlay.
    val topModalIndex = allUpdates.asSequence()
      .flatMap { it.updates.asSequence().map { update -> update.overlay } }
      .indexOfLast { it is ModalOverlay }

    // Z index of the dialog session being updated.
    var updatingSessionIndex = 0

    val allNewSessions = mutableListOf<DialogSession>()
    val viewStates = mutableMapOf<String, KeyAndBundle>()
    allUpdates.forEach { idAndUpdates ->
      val updatedSessions = mutableListOf<DialogSession>()

      // We're building an object that the next LayeredDialogSessions can use to
      // find an existing dialog that matches a given Overlay. Any
      // incompatible dialog that we skip on the way to find a match is destroyed.
      val oldSessionFinder = OldSessionFinder { overlay ->

        // First we iterate through the existing windows to find one that belongs
        // to this group and matches the overlay.
        while (establishedSessionsIterator.hasNext()) {
          val (id, session) = establishedSessionsIterator.next()
          if (idAndUpdates.id == id && session.canShow(overlay)) return@OldSessionFinder session

          // Can't update this session from this Overlay, save its view state and destroy it.
          // If it was just out of z order, a new one with matching id will be made and restored.
          session.save()?.let { viewStates[id.toString() + session.savedStateKey] = it }
          session.destroyDialog(saveViewState = true)
          continue
        }

        // There are no established windows left.
        return@OldSessionFinder null
      }

      idAndUpdates.updates.forEach { update ->
        val covered = updatingSessionIndex < topModalIndex
        updatedSessions += update.doUpdate(oldSessionFinder, covered).also { session ->
          viewStates.remove(idAndUpdates.id.toString() + session.savedStateKey)
            ?.let { session.restore(it) }
        }
        updatingSessionIndex++
      }
      idAndUpdates.onSessionsUpdated(updatedSessions)
      allNewSessions += updatedSessions
    }

    establishedSessions.first().onRootUpdateFinished?.invoke(allNewSessions)
    establishedSessions.clear()
    allUpdates.clear()
  }

  override fun toString(): String {
    return "DialogCollator(" +
      "updates=$allUpdates, " +
      "establishedSessions=$establishedSessions, " +
      "expectedUpdates=$expectedUpdates" +
      ")"
  }

  companion object : ViewEnvironmentKey<DialogCollator>() {
    override val default: DialogCollator
      get() = error("Call ViewEnvironment.establishDialogCollator first.")
  }
}
