package com.squareup.workflow1.ui.container

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.View
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.AlertOverlay.Button
import com.squareup.workflow1.ui.container.AlertOverlay.Button.NEGATIVE
import com.squareup.workflow1.ui.container.AlertOverlay.Button.NEUTRAL
import com.squareup.workflow1.ui.container.AlertOverlay.Button.POSITIVE
import com.squareup.workflow1.ui.container.AlertOverlay.Event.ButtonClicked
import com.squareup.workflow1.ui.container.AlertOverlay.Event.Canceled

@WorkflowUiExperimentalApi
internal object AlertOverlayDialogFactory : OverlayDialogFactory<AlertOverlay> {
  override val type = AlertOverlay::class

  override fun buildDialog(
    initialRendering: AlertOverlay,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): AlertDialog {
    return AlertDialog.Builder(context, initialEnvironment[AlertDialogThemeResId])
      .create()
  }

  override fun updateDialog(
    dialog: Dialog,
    rendering: AlertOverlay,
    environment: ViewEnvironment
  ) {
    val alertDialog = dialog as AlertDialog

    if (rendering.cancelable) {
      alertDialog.setOnCancelListener { rendering.onEvent(Canceled) }
      alertDialog.setCancelable(true)
    } else {
      alertDialog.setCancelable(false)
    }

    for (button in Button.values()) {
      rendering.buttons[button]
        ?.let { name ->
          alertDialog.setButton(button.toId(), name) { _, _ ->
            rendering.onEvent(ButtonClicked(button))
          }
        }
        ?: run {
          alertDialog.getButton(button.toId())
            ?.visibility = View.INVISIBLE
        }
    }

    alertDialog.setMessage(rendering.message)
    alertDialog.setTitle(rendering.title)
  }

  private fun Button.toId(): Int = when (this) {
    POSITIVE -> DialogInterface.BUTTON_POSITIVE
    NEGATIVE -> DialogInterface.BUTTON_NEGATIVE
    NEUTRAL -> DialogInterface.BUTTON_NEUTRAL
  }
}
