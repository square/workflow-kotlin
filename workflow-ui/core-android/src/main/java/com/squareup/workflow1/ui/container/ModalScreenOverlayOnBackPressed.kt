package com.squareup.workflow1.ui.container

import android.view.View
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.ModalScreenOverlayOnBackPressed.Handler
import com.squareup.workflow1.ui.onBackPressedDispatcherOwnerOrNull

/**
 * The function called to handle back button events in [Dialog][android.app.Dialog]s built
 * by [ModalScreenOverlayDialogFactory]. The default implementation uses the
 * [Activity][android.app.Activity]'s
 * [OnBackPressedDispatcher][androidx.activity.OnBackPressedDispatcher].
 *
 * This is a hook to accommodate apps that have a back button handling scheme
 * that predates `OnBackPressedDispatcher`.
 */
@WorkflowUiExperimentalApi
public object ModalScreenOverlayOnBackPressed : ViewEnvironmentKey<Handler>(
  type = Handler::class
) {
  public fun interface Handler {
    /**
     * Called when the device back button is pressed and a dialog built by a
     * [ModalScreenOverlayDialogFactory] has window focus.
     *
     * @return true if the back press event was consumed
     */
    public fun onBackPressed(contentView: View): Boolean
  }

  override val default: Handler = Handler { view ->
    view.context.onBackPressedDispatcherOwnerOrNull()
      ?.onBackPressedDispatcher
      ?.let {
        if (it.hasEnabledCallbacks()) it.onBackPressed()
      }
    true
  }
}
