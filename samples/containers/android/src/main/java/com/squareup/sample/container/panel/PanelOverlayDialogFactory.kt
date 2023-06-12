package com.squareup.sample.container.panel

import android.content.Context
import android.graphics.Rect
import androidx.appcompat.app.AppCompatDialog
import com.squareup.sample.container.R
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.OverlayDialogFactory
import com.squareup.workflow1.ui.container.OverlayDialogHolder
import com.squareup.workflow1.ui.container.setBounds
import com.squareup.workflow1.ui.container.setContent
import kotlin.reflect.KClass

/**
 * Android support for [PanelOverlay].
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal object PanelOverlayDialogFactory : OverlayDialogFactory<PanelOverlay<Screen>> {
  override val type: KClass<in PanelOverlay<Screen>> = PanelOverlay::class

  override fun buildDialog(
    initialRendering: PanelOverlay<Screen>,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): OverlayDialogHolder<PanelOverlay<Screen>> {
    val dialog = AppCompatDialog(context, R.style.PanelDialog)

    val realHolder = dialog.setContent(initialRendering, initialEnvironment)

    return object : OverlayDialogHolder<PanelOverlay<Screen>> by realHolder {
      override val onUpdateBounds: ((Rect) -> Unit) = { bounds ->
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
  }
}
