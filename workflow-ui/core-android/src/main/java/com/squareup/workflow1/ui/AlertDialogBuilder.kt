package com.squareup.workflow1.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.View.INVISIBLE
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import com.squareup.workflow1.ui.AlertModalRendering.Button
import com.squareup.workflow1.ui.AlertModalRendering.Button.NEGATIVE
import com.squareup.workflow1.ui.AlertModalRendering.Button.NEUTRAL
import com.squareup.workflow1.ui.AlertModalRendering.Button.POSITIVE
import com.squareup.workflow1.ui.AlertModalRendering.Event.ButtonClicked
import com.squareup.workflow1.ui.AlertModalRendering.Event.Canceled

@WorkflowUiExperimentalApi
class AlertDialogBuilder(
  @StyleRes private val dialogThemeResId: Int = 0
) : DialogBuilder<AlertModalRendering> {
  override val type = AlertModalRendering::class

  override fun buildDialog(
    initialRendering: AlertModalRendering,
    initialViewEnvironment: ViewEnvironment,
    context: Context
  ): Dialog {
    return AlertDialog.Builder(context, dialogThemeResId)
        .create().also { dialog ->
          dialog.bindDisplayFunction(
              initialRendering,
              initialViewEnvironment
          ) { newRendering, _ ->
            if (newRendering.cancelable) {
              dialog.setOnCancelListener { newRendering.onEvent(Canceled) }
              dialog.setCancelable(true)
            } else {
              dialog.setCancelable(false)
            }

            for (button in Button.values()) {
              newRendering.buttons[button]
                  ?.let { name ->
                    dialog.setButton(button.toId(), name) { _, _ ->
                      newRendering.onEvent(ButtonClicked(button))
                    }
                  }
                  ?: run {
                    dialog.getButton(button.toId())?.visibility = INVISIBLE
                  }
            }

            dialog.setMessage(newRendering.message)
            dialog.setTitle(newRendering.title)
          }
        }
  }

  private fun Button.toId(): Int = when (this) {
    POSITIVE -> DialogInterface.BUTTON_POSITIVE
    NEGATIVE -> DialogInterface.BUTTON_NEGATIVE
    NEUTRAL -> DialogInterface.BUTTON_NEUTRAL
  }
}
