package com.squareup.sample.container.panel

import android.app.Dialog
import android.graphics.Rect
import com.squareup.sample.container.R
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.OverlayDialogHolder
import com.squareup.workflow1.ui.container.ScreenOverlayDialogFactory
import com.squareup.workflow1.ui.container.setBounds
import com.squareup.workflow1.ui.container.setContent
import com.squareup.workflow1.ui.show

/**
 * Android support for [PanelOverlay].
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal object PanelOverlayDialogFactory :
  ScreenOverlayDialogFactory<Screen, PanelOverlay<Screen>>(
    type = PanelOverlay::class
  ) {
  /**
   * Forks the default implementation to apply [R.style.PanelDialog] for
   * enter and exit animation, and to customize [bounds][OverlayDialogHolder.onUpdateBounds].
   */
  override fun buildDialogWithContent(
    initialRendering: PanelOverlay<Screen>,
    initialEnvironment: ViewEnvironment,
    content: ScreenViewHolder<Screen>
  ): OverlayDialogHolder<PanelOverlay<Screen>> {
    val dialog = Dialog(content.view.context, R.style.PanelDialog)
    dialog.setContent(content)

    return OverlayDialogHolder(
      initialEnvironment = initialEnvironment,
      dialog = dialog,
      onUpdateBounds = { bounds ->
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
    ) { overlayRendering, environment ->
      content.show(overlayRendering.content, environment)
    }
  }
}
