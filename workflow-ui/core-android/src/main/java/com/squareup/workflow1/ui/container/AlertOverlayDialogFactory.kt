package com.squareup.workflow1.ui.container

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.view.View.GONE
import android.view.View.VISIBLE
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.AlertOverlay.Button
import com.squareup.workflow1.ui.container.AlertOverlay.Button.NEGATIVE
import com.squareup.workflow1.ui.container.AlertOverlay.Button.NEUTRAL
import com.squareup.workflow1.ui.container.AlertOverlay.Button.POSITIVE
import com.squareup.workflow1.ui.container.AlertOverlay.Event.ButtonClicked
import com.squareup.workflow1.ui.container.AlertOverlay.Event.Canceled
import kotlin.reflect.KClass

/**
 * Default [OverlayDialogFactory] for [AlertOverlay].
 *
 * This class is non-final for ease of customization of [AlertOverlay] handling,
 * see [OverlayDialogFactoryFinder] for details.
 */
@WorkflowUiExperimentalApi
public open class AlertOverlayDialogFactory : OverlayDialogFactory<AlertOverlay> {
  override val type: KClass<AlertOverlay> = AlertOverlay::class

  open override fun buildDialog(
    initialRendering: AlertOverlay,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): AlertDialog {
    return AlertDialog.Builder(context, initialEnvironment[AlertDialogThemeResId])
      .create().apply {
        for (button in Button.values()) {
          // We want to be able to update the alert while it's showing, including to maybe
          // show more buttons than were there originally. The API for Android's `AlertDialog`
          // makes you think you can do that, but it actually doesn't work. So we force
          // `AlertDialog.Builder` to show every possible button; then we hide them all;
          // and then we manage their visibility ourselves at update time.
          //
          // We also don't want Android to tear down the dialog without our say so --
          // again, we might need to update the thing. But there is a dismiss call
          // built in to click handers put in place by `AlertDialog`. So, when we're
          // preflighting every possible button, we put garbage click handlers in place.
          // Then we replace them with our own, again at update time, by setting each live
          // button's click handler directly, without letting `AlertDialog` interfere.
          //
          // https://github.com/square/workflow-kotlin/issues/138
          //
          // Why " "? An empty string means no button.
          setButton(button.toId(), " ") { _, _ -> }
        }
      }
  }

  open override fun updateDialog(
    dialog: Dialog,
    rendering: AlertOverlay,
    environment: ViewEnvironment
  ) {
    (dialog as AlertDialog).apply {
      if (rendering.cancelable) {
        setOnCancelListener { rendering.onEvent(Canceled) }
        setCancelable(true)
      } else {
        setCancelable(false)
      }

      setMessage(rendering.message)
      setTitle(rendering.title)

      // The buttons won't actually exist until the dialog is showing.
      if (isShowing) updateButtonsOnShow(rendering) else setOnShowListener {
        updateButtonsOnShow(rendering)
      }
    }
  }

  protected fun Button.toId(): Int = when (this) {
    POSITIVE -> DialogInterface.BUTTON_POSITIVE
    NEGATIVE -> DialogInterface.BUTTON_NEGATIVE
    NEUTRAL -> DialogInterface.BUTTON_NEUTRAL
  }

  protected fun AlertDialog.updateButtonsOnShow(rendering: AlertOverlay) {
    setOnShowListener(null)

    for (button in Button.values()) getButton(button.toId()).visibility = GONE

    for (entry in rendering.buttons.entries) {
      getButton(entry.key.toId())?.apply {
        setOnClickListener { rendering.onEvent(ButtonClicked(entry.key)) }
        text = entry.value
        visibility = VISIBLE
      }
    }
  }
}
