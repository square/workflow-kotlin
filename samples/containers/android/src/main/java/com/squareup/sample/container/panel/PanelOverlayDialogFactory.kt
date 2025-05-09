package com.squareup.sample.container.panel

import android.content.Context
import android.graphics.Rect
import androidx.appcompat.app.AppCompatDialog
import com.squareup.sample.container.R
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.navigation.OverlayDialogFactory
import com.squareup.workflow1.ui.navigation.OverlayDialogHolder
import com.squareup.workflow1.ui.navigation.asDialogHolderWithContent
import com.squareup.workflow1.ui.navigation.setBounds
import kotlin.reflect.KClass

/**
 * Android support for [PanelOverlay].
 */
internal object PanelOverlayDialogFactory : OverlayDialogFactory<PanelOverlay<*>> {
  override val type: KClass<in PanelOverlay<*>> = PanelOverlay::class

  override fun buildDialog(
    initialRendering: PanelOverlay<*>,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): OverlayDialogHolder<PanelOverlay<*>> {
    val dialog = AppCompatDialog(context, R.style.PanelDialog)

    val realHolder = dialog.asDialogHolderWithContent(initialRendering, initialEnvironment)

    // We replace the default onUpdateBounds function with one that gives the
    // panel a square shape on tablets. See OverlayDialogFactory for more details
    // on the bounds mechanism.
    return object : OverlayDialogHolder<PanelOverlay<*>> by realHolder {
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
