package com.squareup.workflow1.ui.navigation

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.View.GONE
import android.view.View.VISIBLE
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.navigation.AlertOverlay.Button
import com.squareup.workflow1.ui.navigation.AlertOverlay.Button.NEGATIVE
import com.squareup.workflow1.ui.navigation.AlertOverlay.Button.NEUTRAL
import com.squareup.workflow1.ui.navigation.AlertOverlay.Button.POSITIVE
import com.squareup.workflow1.ui.navigation.AlertOverlay.Event.ButtonClicked
import com.squareup.workflow1.ui.navigation.AlertOverlay.Event.Canceled
import kotlin.reflect.KClass

/**
 * Default [OverlayDialogFactory] for [AlertOverlay], uses [AlertDialog].
 *
 * See [AlertDialog.toDialogHolder] to use [AlertDialog] for other purposes.
 *
 * - To customize [AlertDialog] theming, see [AlertDialogThemeResId]
 * - To customize how [AlertOverlay] is handled more generally, set up a
 *   custom [OverlayDialogFactoryFinder].
 */
@WorkflowUiExperimentalApi
internal class AlertOverlayDialogFactory : OverlayDialogFactory<AlertOverlay> {
  override val type: KClass<AlertOverlay> = AlertOverlay::class

  override fun buildDialog(
    initialRendering: AlertOverlay,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): OverlayDialogHolder<AlertOverlay> =
    AlertDialog.Builder(
      context,
      initialEnvironment[com.squareup.workflow1.ui.navigation.AlertDialogThemeResId]
    )
      .create()
      .toDialogHolder(initialEnvironment)
}

/**
 * Wraps the receiver in in an [OverlayDialogHolder] that is able to update its
 * buttons as new [AlertOverlay] renderings are received.
 */
@WorkflowUiExperimentalApi
public fun AlertDialog.toDialogHolder(
  initialEnvironment: ViewEnvironment
): OverlayDialogHolder<AlertOverlay> {
  for (button in Button.values()) {
    // We want to be able to update the alert while it's showing, including to maybe
    // show more buttons than were there originally. The API for Android's `AlertDialog`
    // makes you think you can do that, but it actually doesn't work. So we force
    // `AlertDialog.Builder` to show every possible button; then we hide them all;
    // and then we manage their visibility ourselves at update time.
    //
    // We also don't want Android to tear down the dialog without our say so --
    // again, we might need to update the thing. But there is a dismiss call
    // built in to click handlers put in place by `AlertDialog`. So, when we're
    // preflighting every possible button, we put garbage click handlers in place.
    // Then we replace them with our own, again at update time, by setting each live
    // button's click handler directly, without letting `AlertDialog` interfere.
    //
    // https://github.com/square/workflow-kotlin/issues/138
    //
    // Why " "? An empty string means no button.
    setButton(button.toId(), " ") { _, _ -> }
  }

  return OverlayDialogHolder(
    initialEnvironment = initialEnvironment,
    dialog = this,
    onUpdateBounds = null
  ) { rendering, _ ->
    with(this) {
      if (rendering.cancelable) {
        setOnCancelListener { rendering.onEvent(Canceled) }
        setCancelable(true)
      } else {
        setCancelable(false)
      }

      setMessage(rendering.message)
      setTitle(rendering.title)

      // The buttons won't actually exist until the dialog is showing.
      if (isShowing) {
        updateButtonsOnShow(rendering)
      } else {
        setOnShowListener {
          updateButtonsOnShow(rendering)
        }
      }
    }
  }
}

@WorkflowUiExperimentalApi
private fun Button.toId(): Int = when (this) {
  POSITIVE -> DialogInterface.BUTTON_POSITIVE
  NEGATIVE -> DialogInterface.BUTTON_NEGATIVE
  NEUTRAL -> DialogInterface.BUTTON_NEUTRAL
}

@WorkflowUiExperimentalApi
private fun AlertDialog.updateButtonsOnShow(rendering: AlertOverlay) {
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
