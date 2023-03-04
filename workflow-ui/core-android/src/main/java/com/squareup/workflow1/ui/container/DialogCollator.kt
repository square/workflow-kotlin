package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.DialogCollator.SessionAndViewStateMap
import java.util.UUID

/**
 * Helper provided to [DialogSessionUpdate.doUpdate] to give access
 * to an existing [DialogSession] able to display a given [Overlay].
 */
@WorkflowUiExperimentalApi
internal fun interface OldSessions {
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
 * [overlay]. Provided with an [OldSessions] instance that provides access to the
 * existing session, if any, and a `covered: Boolean` param that indicates
 * if there is a [ModalOverlay] above [overlay]
 */
@WorkflowUiExperimentalApi
internal class DialogSessionUpdate(
  val overlay: Overlay,
  val doUpdate: (
    oldSessions: OldSessions,
    covered: Boolean
  ) -> DialogSession
)

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
 */
@WorkflowUiExperimentalApi
internal fun ViewEnvironment.establishDialogCollator(
  id: UUID,
  existingSessions: List<DialogSession>
): ViewEnvironment {
  val collatorOrNull = map[DialogCollator]
  val collator = (collatorOrNull as? DialogCollator) ?: DialogCollator()

  collator.expectedUpdates++
  collator.establishedSessions.add(
    SessionAndViewStateMap(id, existingSessions)
  )

  return if (collatorOrNull == null) this + (DialogCollator to collator) else this
}

/**
 * Resource shared by a recursive set of [LayeredDialogSessions], used to
 * ensure that the windows they define are stacked in the correct order and
 * stay that way across updates.
 *
 * Android notoriously doesn't give us any control over the z order of windows
 * other than taking care to [show][android.app.Dialog.show] them in the right
 * order, so keeping a pile of windows in sync with the shifting order of the
 * defining list of [Overlay] rendering models is hard. [DialogCollator] manages
 * that process. On each update it builds complete knowledge of the existing stack
 * of windows, and so is able to decide if any instances need to be
 *
 * [LayeredDialogSessions.update] starts by calling [ViewEnvironment.establishDialogCollator],
 * to register its existing list of [DialogSession], and provide a
 * `MutableMap<String, KeyAndBundle>` that can be populated by the
 * [classic view state][DialogSession.save] of any
 */
@WorkflowUiExperimentalApi
internal class DialogCollator {
  internal class SessionAndViewStateMap(
    val id: UUID,
    val sessions: List<DialogSession>
  )

  private class UpdateBlock(
    val id: UUID,
    val updates: List<DialogSessionUpdate>,
    val callback: (List<DialogSession>) -> Unit
  )

  private val updateBlocks = mutableListOf<UpdateBlock>()

  internal val establishedSessions = mutableListOf<SessionAndViewStateMap>()
  internal var expectedUpdates = 0

  /**
   * Follow up call to [ViewEnvironment.establishDialogCollator]. Adds a set
   * of update operations to be applied to the [DialogSession] set provided
   * to that call. The updates are not applied until a matching call
   * to [scheduleUpdates] has been received for each call to [establishDialogCollator].
   *
   * @param id unique runtime-only id used to pair calls to [establishDialogCollator]
   * and [DialogCollator.scheduleUpdates]
   *
   * @param updates list of new [Overlay]s to show, each paired with a function
   * to create or update a [DialogSession] to show it.
   *
   * @param callback called immediately after the given [updates] are applied.
   * Provides the updated list of [DialogSession] that should replace those that
   * were provided to [establishDialogCollator].
   */
  internal fun scheduleUpdates(
    id: UUID,
    updates: List<DialogSessionUpdate>,
    callback: (List<DialogSession>) -> Unit
  ) {
    check(expectedUpdates > 0) {
      "Each update() call must be preceded by a call to ViewEnvironment.establishDialogCollator, " +
        "but expectedUpdates is $expectedUpdates"
    }

    this.updateBlocks.add(UpdateBlock(id, updates, callback))
    if (--expectedUpdates == 0) doUpdate()
  }

  private fun getUpdatedModalIndex(): Int {
    val overlays = updateBlocks.flatMap { it.updates.map { update -> update.overlay } }
    return overlays.indexOfFirst { it is ModalOverlay }
  }

  private fun doUpdate() {
    val visibleSessionsIterator = establishedSessions.asReversed().asSequence().flatMap {
      it.sessions.map { session -> Pair(it.id, session) }
    }.iterator()
    val hiddenSessions = mutableListOf<Pair<UUID, DialogSession>>()
    val modalIndex = getUpdatedModalIndex()

    var updateBlocksOffset = 0
    updateBlocks.forEachIndexed { i, updateBlock ->
      val updatedSessions = mutableListOf<DialogSession>()
      val covered = i + updateBlocksOffset < modalIndex

      val oldSessions = OldSessions { overlay ->

        // First we iterate through the existing windows to find one that belongs
        // to this group, and matches the next overlay. We hide any that were skipped
        // (via Dialog.dismiss under the hood), but hold on to them in case they can
        // still be used -- when unhidden (Dialog.show), they pop to the front.
        while (visibleSessionsIterator.hasNext()) {
          val (id, session) = visibleSessionsIterator.next()
          if (updateBlock.id == id && session.canShow(overlay)) return@OldSessions session
          session.setVisible(false)
          hiddenSessions.add(Pair(id, session))
          continue
        }

        // There are no visible windows left. See if any of the ones that were
        // hidden because they were out of order can be shown again, which
        // will bring them to the front. Yes, this is a linear search. If
        // we start managing enough windows for that to be a concern, we
        // will have much bigger problems long before that matters.
        val hiddenIndex = hiddenSessions.indexOfFirst {
          val (id, session) = it
          updateBlock.id == id && session.canShow(overlay)
        }

        return@OldSessions hiddenSessions.getOrNull(hiddenIndex)?.second?.also {
          it.setVisible(true)
        }
      }

      updateBlock.updates.forEach { update ->
        updatedSessions += update.doUpdate(oldSessions, covered)
      }
      updateBlock.callback(updatedSessions)
      updateBlocksOffset += updateBlock.updates.size
    }

    establishedSessions.clear()
    updateBlocks.clear()
  }

  override fun toString(): String {
    return "DialogCollator(" +
      "updates=$updateBlocks, " +
      "establishedSessions=$establishedSessions, " +
      "expectedUpdates=$expectedUpdates" +
      ")"
  }

  companion object : ViewEnvironmentKey<DialogCollator>(DialogCollator::class) {
    override val default: DialogCollator
      get() = error("Call ViewEnvironment.establishDialogCollator first.")
  }
}
