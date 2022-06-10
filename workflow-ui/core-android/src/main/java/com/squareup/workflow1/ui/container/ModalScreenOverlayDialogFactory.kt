package com.squareup.workflow1.ui.container

import android.app.Dialog
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.KeyEvent
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_ESCAPE
import android.view.View
import android.view.Window
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.startShowing
import com.squareup.workflow1.ui.toViewFactory
import kotlin.reflect.KClass

/**
 * Default [OverlayDialogFactory] for [ModalScreenOverlay].
 *
 * This class is non-final for ease of customization of [ModalScreenOverlay] handling,
 * see [OverlayDialogFactoryFinder] for details. It is also convenient to use as a
 * base class for custom [ScreenOverlay] rendering types.
 *
 * Dialogs built by this class are compatible with [View.backPressedHandler], and
 * honor the [ModalArea] constraint placed in the [ViewEnvironment] by the
 * standard [BodyAndModalsScreen] container.
 *
 * Ironically, [Dialog] instances are created with [FLAG_NOT_TOUCH_MODAL], to ensure
 * that events outside of the bounds reported by [updateBounds] reach views in
 * lower windows. See that method for details.
 */
@WorkflowUiExperimentalApi
public open class ModalScreenOverlayDialogFactory<S: Screen, O : ScreenOverlay<S>>(
  override val type: KClass<in O>
) : OverlayDialogFactory<O> {

  /**
   * Called from [buildDialog]. Builds (but does not show) the [Dialog] to
   * display a [contentView] built for a [ScreenOverlay.content].
   *
   * Custom implementations are not required to call `super`.
   *
   * Default implementation calls [Dialog.setModalContent].
   */
  public open fun buildDialogWithContentView(content: ScreenViewHolder<S>): Dialog {
    return Dialog(content.view.context).also { it.setModalContent(content.view) }
  }

  /**
   * If the [ScreenOverlay] displayed by a [dialog] created by this
   * factory is contained in a [BodyAndModalsScreen], this method will
   * be called to report the bounds of the managing view, as reported by [ModalArea].
   * It is expected that such a dialog will be restricted to those bounds.
   *
   * Honoring this contract makes it easy to define areas of the display
   * that are outside of the "shadow" of a modal dialog. Imagine an app
   * with a status bar that should not be covered by modals.
   *
   * The default implementation calls straight through to the [Dialog.setBounds] function
   * provided below. Custom implementations are not required to call `super`.
   *
   * @see Dialog.setBounds
   */
  public open fun updateBounds(
    dialog: Dialog,
    bounds: Rect
  ) {
    dialog.setBounds(bounds)
  }

  final override fun buildDialog(
    initialRendering: O,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): OverlayDialogHolder<O> {
    val contentViewHolder = initialRendering.content.toViewFactory(initialEnvironment)
      .startShowing(initialRendering.content, initialEnvironment, context).apply {
        environment[ModalScreenOverlayBackButtonHelper].initialize(view)
      }

    return buildDialogWithContentView(contentViewHolder).let { dialog ->
      val window = requireNotNull(dialog.window) { "Dialog must be attached to a window." }

      val realWindowCallback = window.callback
      window.callback = object : Window.Callback by realWindowCallback {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
          val isBackPress = (event.keyCode == KEYCODE_BACK || event.keyCode == KEYCODE_ESCAPE) &&
            event.action == ACTION_UP

          return when {
            isBackPress -> contentViewHolder.environment[ModalScreenOverlayBackButtonHelper]
              .onBackPressed(contentViewHolder.view) == true
            else -> realWindowCallback.dispatchKeyEvent(event)
          }
        }
      }

      window.setFlags(FLAG_NOT_TOUCH_MODAL, FLAG_NOT_TOUCH_MODAL)
      dialog.maintainBounds(contentViewHolder.environment) { d, b -> updateBounds(d, Rect(b)) }

      OverlayDialogHolder(initialEnvironment, dialog) { overlayRendering, environment ->
        contentViewHolder.show(overlayRendering.content, environment)
      }
    }
  }
}

/**
 * The default implementation of [ModalScreenOverlayDialogFactory.buildDialogWithContentView].
 *
 * Sets the [background][Window.setBackgroundDrawable] of the receiver's [Window] based
 * on its theme, if any, or else `null`. (Setting the background to `null` ensures the window
 * can go full bleed.)
 */
@OptIn(WorkflowUiExperimentalApi::class)
public fun Dialog.setModalContent(contentView: View) {
  setCancelable(false)
  setContentView(contentView)

  // Welcome to Android. Nothing workflow-related here, this is just how one
  // finds the window background color for the theme. I sure hope it's better in Compose.
  val maybeWindowColor = TypedValue()
  context.theme.resolveAttribute(android.R.attr.windowBackground, maybeWindowColor, true)

  val background =
    if (maybeWindowColor.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
      ColorDrawable(maybeWindowColor.data)
    } else {
      // If we don't at least set it to null, the window cannot go full bleed.
      null
    }
  window!!.setBackgroundDrawable(background)
}
