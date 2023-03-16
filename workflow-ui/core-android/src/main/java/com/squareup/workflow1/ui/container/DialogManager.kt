package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
internal class DialogSessionUpdate(
  val overlay: Overlay,
  val doUpdate: (
    overlay: Overlay,
    oldSessions: Iterator<DialogSession>,
    covered: Boolean
  ) -> DialogSession
)

/**
 * Init method called at the start of [LayeredDialogSessions.update].
 * Ensures that there is a single [DialogManager] instance shared by
 * an entire recursive hierarchy of [LayeredDialogSessions] for each
 * update pass.
 *
 * Each call to [establishDialogManager] must be matched with a call to
 * [DialogManager.scheduleUpdates]. This balance lets us know when a set of recursive
 * [LayeredDialogSessions.update] calls is complete, so that we can manage
 * the entire set of [Dialog][android.app.Dialog]. This is the only way that
 * we can ensure that the `Dialog`s are stacked correctly.
 */
@WorkflowUiExperimentalApi
internal fun ViewEnvironment.establishDialogManager(
  existingSessions: List<DialogSession>
): ViewEnvironment {
  val managerOrNull = map[DialogManager]
  val manager = (managerOrNull as? DialogManager)?.also {
    check(it.expectedUpdates > 0) {
      "When continuing to establish a dialog update pass, expectedUpdates should be > 0, " +
        "found $it."
    }
  } ?: DialogManager()

  manager.expectedUpdates++
  manager.establishedSessions += existingSessions

  return if (managerOrNull == null) this + (DialogManager to manager) else this
}

@WorkflowUiExperimentalApi
internal class DialogManager {
  private class UpdateBlock(
    val updates: List<DialogSessionUpdate>,
    val callback: (List<DialogSession>) -> Unit
  )

  private val blocks = mutableListOf<UpdateBlock>()

  internal val establishedSessions = mutableListOf<DialogSession>()
  internal var expectedUpdates = 0

  internal fun scheduleUpdates(
    updates: List<DialogSessionUpdate>,
    callback: (List<DialogSession>) -> Unit
  ) {
    check(expectedUpdates > 0) {
      "Each update() call must be preceded by a call to ViewEnvironment.establishDialogManager, " +
        "but expectedUpdates is $expectedUpdates"
    }

    this.blocks.add(UpdateBlock(updates, callback))
    if (--expectedUpdates == 0) doUpdate()
  }

  private fun getModalIndex(): Int {
    val overlays = blocks.asSequence().flatMap { it.updates.map { update -> update.overlay } }
    return overlays.indexOfFirst { it is ModalOverlay }
  }

  private fun doUpdate() {
    val updatedSessions = mutableListOf<DialogSession>()
    val oldSessionIterator = establishedSessions.iterator()
    val modalIndex = getModalIndex()

    var offset = 0
    blocks.forEachIndexed { i, block ->
      val blockSessions = mutableListOf<DialogSession>()
      val covered = i + offset < modalIndex

      block.updates.forEach { update ->
        blockSessions += update.doUpdate(update.overlay, oldSessionIterator, covered)
      }
      updatedSessions += blockSessions
      block.callback(blockSessions)
      offset += block.updates.size
    }

    (establishedSessions - updatedSessions.toSet()).forEach { it.dismiss() }
    establishedSessions.clear()
    blocks.clear()
  }

  override fun toString(): String {
    return "DialogManager(" +
      "updates=$blocks, " +
      "establishedSessions=$establishedSessions, " +
      "expectedUpdates=$expectedUpdates" +
      ")"
  }

  companion object : ViewEnvironmentKey<DialogManager>(DialogManager::class) {
    override val default: DialogManager
      get() = error("Call ViewEnvironment.withDialogManager first.")
  }
}
