package com.squareup.sample.container.panel

import android.app.Dialog
import android.graphics.Rect
import android.view.View
import com.squareup.sample.container.R
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.ScreenOverlayDialogFactory
import com.squareup.workflow1.ui.container.setModalContent

/**
 * Android support for [PanelOverlay].
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal object PanelOverlayDialogFactory : ScreenOverlayDialogFactory<PanelOverlay<*>>(
  type = PanelOverlay::class
) {
  /**
   * Forks the default implementation to apply [R.style.PanelDialog], for
   * enter and exit animation.
   */
  override fun buildDialogWithContentView(contentView: View): Dialog {
    return Dialog(contentView.context, R.style.PanelDialog).also {
      it.setModalContent(contentView)
    }
  }

  override fun updateBounds(
    dialog: Dialog,
    bounds: Rect
  ) {
    val refinedBounds: Rect = if (!dialog.context.isTablet) {
      // On a phone, fill the bounds entirely.
      bounds
    } else {
      if (bounds.height() > bounds.width()) {
        val margin = bounds.height() - bounds.width()
        val topDelta = margin / 2
        val bottomDelta = margin - topDelta
        Rect(bounds).apply {
          top = bounds.top + topDelta
          bottom = bounds.bottom - bottomDelta
        }
      } else {
        val margin = bounds.width() - bounds.height()
        val leftDelta = margin / 2
        val rightDelta = margin - leftDelta
        Rect(bounds).apply {
          left = bounds.left + leftDelta
          right = bounds.right - rightDelta
        }
      }
    }
    super.updateBounds(dialog, refinedBounds)
  }
}
