package com.squareup.workflow1.ui.container

import android.content.Context
import android.graphics.Rect
import androidx.lifecycle.LifecycleOwner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowSavedStateRegistryAggregator
import kotlinx.coroutines.flow.StateFlow

/**
 * Collects a set of [Overlay] renderings and everything needed to create and manage
 * the [Dialog][android.app.Dialog] instances they define. Used by a central [DialogManager]
 * that coordinates creating, showing and dismissing all the `Dialog`s requested
 * by an arbitrary hierarchy of [LayeredDialogSessions].
 */
@WorkflowUiExperimentalApi
internal class DialogBlock(
  internal val context: Context,
  internal val bounds: StateFlow<Rect>,
  internal val overlays: List<Overlay>,
  internal val environment: ViewEnvironment,
  internal val getParentLifecycleOwner: () -> LifecycleOwner,
  internal val stateRegistryAggregator: WorkflowSavedStateRegistryAggregator,
  internal val acceptSessions: (List<DialogSession>) -> Unit
)

/**
 * Init method called at the start of [LayeredDialogSessions.update].
 * Ensures that there is a single [DialogManager] instance shared by
 * an entire hierarchy of [LayeredDialogSessions]. Caller is responsible
 * for holding a pointer to the [DialogManager] that is established in the returned
 * [ViewEnvironment], and passing it back again on the next call.
 *
 * Each call to [establishDialogManager] must be matched with a call to
 * [DialogManager.update]. This balance lets us know when a set of recursive
 * [LayeredDialogSessions.update] calls is complete, so that we can manage
 * the entire set of [Dialog][android.app.Dialog]. This is the only way that
 * we can ensure that the `Dialog`s are layered correctly.
 *
 * @param given the [DialogManager] that was found in the [ViewEnvironment]
 * returned by the previous call to this method, or null if this is the
 * first call
 */
@WorkflowUiExperimentalApi
internal fun ViewEnvironment.establishDialogManager(
  given: DialogManager?
): ViewEnvironment {
  val found = map[DialogManager] as? DialogManager

  if (found == null) {
    // We are the root. Use given existing, or if it's null
    // (this is the first ever rendering pass), make one.
    val manager = given ?: DialogManager()

    check(manager.expectedUpdates <= 0) {
      "When establishing new dialog update pass expectedUpdates should be 0, " +
        "found ${manager.expectedUpdates}"
    }
    // Reset for this pass.
    manager.expectedUpdates = 1

    return this + (DialogManager to manager)
  }

  // We found a manager in the environment, so we are not the root.
  // Make sure given, if any, matches it. We don't (yet?) support
  // retroactively introducing a new root. If we get there, we'll
  // need at least shut down everything in given. Bonus points for
  // migrating it to found / new manager.
  check(given == null || given === found) {
    "Cannot use $given as DialogManager because $found is already in $this"
  }

  found.expectedUpdates++
  return this
}

/**
 * Manages all of the [DialogSession]s defined by the [Overlay] renderings of
 * an entire hierarchy of [LayeredDialogSessions] instances, responsible for
 * creating, showing and dismissing them. This central management is required
 * to ensure that the stack of [Dialog][android.app.Dialog]s in play is in the
 * same order as the lists of [Overlay] renderings that define them.
 *
 * [LayeredDialogSessions.update] calls [ViewEnvironment.establishDialogManager]
 * to signal that an update has begun, updates its base view (that is, the
 * view shown beneath a set of `Dialog` windows, and whose bounds defines the area
 * these windows are allowed to cover), and then calls [update] to register the
 * set of [Overlay] instances it wants to be expressed as `Dialog` instances.
 * The set of [Overlay]s is packaged as a [DialogBlock], along with the information
 * needed to express the [Overlay] as a `Dialog`, manage its lifecycle, and a callback
 * hook used to give each [LayeredDialogSessions] access to its particular set of
 * [DialogSession]. The callback is necessary so that each [LayeredDialogSessions]
 * instance can save and restore the view state of its `Dialog` set via along with the
 * view state of its base view.
 *
 * When a [LayeredDialogSessions] updates its base view between calls to [establishDialogManager]
 * and [update], this may lead to recursive calls to [LayeredDialogSessions.update],
 * each bracketed with its own calls to [establishDialogManager] and [update]. We
 * count the calls to the [establishDialogManager] and hold off on executing any `Dialog`
 * operations until a matching number of [update] calls has been received. That's
 * when we know that recursion has finished, and the [DialogManager] has a complete
 * picture of the stack of [Overlay] renderings that is in play. Only then are new
 * `Dialog` instances created and shown, existing ones updated, and unreferenced
 * ones dismissed.
 */
@WorkflowUiExperimentalApi
internal class DialogManager {
  private var blocks = mutableListOf<DialogBlock>()
  internal var expectedUpdates = -1

  private var sessions: List<DialogSession> = emptyList()

  internal fun update(block: DialogBlock) {
    check(expectedUpdates > 0) {
      "each update() call must be preceded by a call to ViewEnvironment.establishDialogManager, " +
        "but expectedUpdates is $expectedUpdates"
    }

    blocks.add(block)
    if (--expectedUpdates == 0) doUpdate()
  }

  private fun getModalIndex(): Int {
    val overlays = blocks.asSequence().flatMap { it.overlays }
    return overlays.indexOfFirst { it is ModalOverlay }
  }

  private fun doUpdate() {
    // On each update we build a new list of the running dialogs, both the
    // existing ones and any new ones. We need this so that we can compare
    // it with the previous list, and see what dialogs close.
    val updatedSessions = mutableListOf<DialogSession>()
    val oldSessionIterator = sessions.iterator()
    val modalIndex = getModalIndex()

    var offset = 0

    blocks.forEachIndexed { i, block ->
      val envPlusBounds = block.environment + OverlayArea(block.bounds)
      val blockSessions = mutableListOf<DialogSession>()

      block.overlays.forEach { overlay ->
        val covered = i + offset < modalIndex
        val dialogEnv = if (covered) envPlusBounds + (CoveredByModal to true) else envPlusBounds

        // We can't insert new dialogs in front of existing ones b/c there is no
        // API to control z order of windows. But we can at least close lower dialogs
        // that disappear from the list while preserving those that remain -- that is,
        // updating [overlay1, overlay2] to [overlay2, overlay3] should preserve the
        // dialog that was already created for overlay2. So for each entry in overlays,
        // we advance to the first compatible old session, if any, and reuse it.
        // The ones that we skip will be discarded and dismissed.

        val oldSessionOrNull = oldSessionIterator.firstCompatible(overlay)

        blockSessions += oldSessionOrNull?.also { it.holder.show(overlay, dialogEnv) }
          ?: overlay.toDialogFactory(dialogEnv)
            .buildDialog(overlay, dialogEnv, block.context)
            .let { holder ->
              holder.onUpdateBounds?.let { updateBounds ->
                holder.dialog.maintainBounds(holder.environment) { b -> updateBounds(b) }
              }

              DialogSession(i, overlay, holder).also { newSession ->
                // Prime the pump, make the first call to OverlayDialog.show to update
                // the new dialog to reflect the first rendering.
                newSession.holder.show(overlay, dialogEnv)
                // And now start the lifecycle machinery and show the dialog window itself.
                newSession.showDialog(
                  block.getParentLifecycleOwner(),
                  block.stateRegistryAggregator
                )
              }
            }
      }

      block.acceptSessions(blockSessions)
      updatedSessions += blockSessions

      offset += block.overlays.size
    }
    blocks.clear()
    (sessions - updatedSessions.toSet()).forEach { it.dismiss() }
    sessions = updatedSessions
  }

  private fun Iterator<DialogSession>.firstCompatible(overlay: Overlay): DialogSession? {
    while (hasNext()) {
      next().takeIf { it.holder.canShow(overlay) }?.let { return it }
    }
    return null
  }

  companion object : ViewEnvironmentKey<DialogManager>(DialogManager::class) {
    override val default: DialogManager
      get() = error("Call ViewEnvironment.withDialogManager first.")
  }
}
