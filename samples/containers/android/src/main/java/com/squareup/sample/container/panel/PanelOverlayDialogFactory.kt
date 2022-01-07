package com.squareup.sample.container.panel

import android.app.Dialog
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import com.squareup.sample.container.R
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.ModalScreenOverlayDialogFactory
import com.squareup.workflow1.ui.container.setBounds

/**
 * Android support for [PanelOverlay].
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal object PanelOverlayDialogFactory : ModalScreenOverlayDialogFactory<PanelOverlay<*>>(
  type = PanelOverlay::class
) {
  override fun buildDialogWithContent(content: View): Dialog {
    val context = content.context
    return Dialog(context, R.style.PanelDialog).also { dialog ->
      dialog.setContentView(content)

      // Welcome to Android. Nothing workflow-related here, this is just how one
      // finds the window background color for the theme. I sure hope it's better in Compose.
      val maybeWindowColor = TypedValue()
      context.theme.resolveAttribute(android.R.attr.windowBackground, maybeWindowColor, true)
      if (
        maybeWindowColor.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT
      ) {
        dialog.window!!.setBackgroundDrawable(ColorDrawable(maybeWindowColor.data))
      }
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
    dialog.setBounds(refinedBounds)
  }
}
