package com.squareup.workflow1.ui.container

import android.view.View
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler
import com.squareup.workflow1.ui.onBackPressedDispatcherOwnerOrNull

/**
 * Functions called to handle back button events in [Dialog][android.app.Dialog]s built
 * by [ScreenOverlayDialogFactory] for renderings of type [ModalOverlay].
 * The default implementation uses the [Activity][android.app.Activity]'s
 * [OnBackPressedDispatcher][androidx.activity.OnBackPressedDispatcher],
 * via [backPressedHandler].
 *
 * This is a hook to allow apps that have back button handling schemes
 * that predate `OnBackPressedDispatcher` to take advantage of [ScreenOverlayDialogFactory]
 * without forking it.
 */
@WorkflowUiExperimentalApi
public interface ModalScreenOverlayBackButtonHelper {
  /**
   * Called immediately after [ScreenViewHolder.show][com.squareup.workflow1.ui.show]
   * for any [Dialog][android.app.Dialog] built by a [ScreenOverlayDialogFactory]
   * to show [ModalOverlay] renderings.
   *
   * The default implementation ensures that [back press handlers][View.backPressedHandler]
   * set in lower windows are blocked.
   */
  public fun onContentViewUpdate(contentView: View) {
    // If the content view has no backPressedHandler, add a no-op one to
    // ensure that the `onBackPressed` call below will not leak up to handlers
    // that should be blocked by this modal session.
    if (contentView.backPressedHandler == null) {
      contentView.backPressedHandler = { }
    }
  }

  /**
   * Called when the device back button is pressed and a modal dialog built by a
   * [ScreenOverlayDialogFactory] has window focus.
   *
   * @return true if the back press event was consumed
   */
  public fun onBackPressed(contentView: View): Boolean {
    contentView.context.onBackPressedDispatcherOwnerOrNull()
      ?.onBackPressedDispatcher
      ?.let {
        if (it.hasEnabledCallbacks()) it.onBackPressed()
      }
    return true
  }

  public companion object : ViewEnvironmentKey<ModalScreenOverlayBackButtonHelper>(
    type = ModalScreenOverlayBackButtonHelper::class
  ) {
    override val default: ModalScreenOverlayBackButtonHelper =
      object : ModalScreenOverlayBackButtonHelper {}
  }
}

@WorkflowUiExperimentalApi
public operator fun ViewEnvironment.plus(
  backButtonHelper: ModalScreenOverlayBackButtonHelper
): ViewEnvironment = this + (ModalScreenOverlayBackButtonHelper to backButtonHelper)
