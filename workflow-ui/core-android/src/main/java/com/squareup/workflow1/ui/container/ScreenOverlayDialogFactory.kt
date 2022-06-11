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
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.startShowing
import com.squareup.workflow1.ui.toViewFactory
import kotlin.reflect.KClass

/**
 * Extensible base implementation of [OverlayDialogFactory] for [ScreenOverlay]
 * types. Also serves as the default factory for [FullScreenOverlay].
 * (See [OverlayDialogFactoryFinder] for guidance on customizing the presentation
 * of [FullScreenOverlay].)
 *
 * Dialogs built by this class are compatible with [View.backPressedHandler], and
 * honor the [OverlayArea] constraint placed in the [ViewEnvironment] by the
 * standard [BodyAndOverlaysScreen] container.
 *
 * Ironically, [Dialog] instances are created with [FLAG_NOT_TOUCH_MODAL], to ensure
 * that events outside of the bounds reported by [updateBounds] reach views in
 * lower windows. See that method for details.
 */
@WorkflowUiExperimentalApi
public open class ScreenOverlayDialogFactory<O : ScreenOverlay<*>>(
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
  public open fun buildDialogWithContentView(contentView: View): Dialog {
    return Dialog(contentView.context).also { it.setModalContent(contentView) }
  }

  /**
   * This method will be called to report the bounds of the managing container view,
   * as reported by [OverlayArea]. Well behaved [ScreenOverlay] dialogs are expected to
   * be restricted to those bounds.
   *
   * Honoring this contract makes it easy to define areas of the display
   * that are outside of the "shadow" of a modal dialog. Imagine an app
   * with a status bar that should not be covered by modals.
   *
   * The default implementation calls straight through to the provided [Dialog.setBounds]
   * function. Custom implementations are not required to call `super`.
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
  ): Dialog {
    // Put a no-op backPressedHandler behind the given rendering, to
    // ensure that the `onBackPressed` call below will not leak up to handlers
    // that should be blocked by this modal session.
    val wrappedContentRendering = BackButtonScreen(initialRendering.content) { }

    val contentViewHolder = wrappedContentRendering.toViewFactory(initialEnvironment)
      .startShowing(wrappedContentRendering, initialEnvironment, context).apply {
        // If the content view has no backPressedHandler, add a no-op one to
        // ensure that the `onBackPressed` call below will not leak up to handlers
        // that should be blocked by this modal session.
        if (view.backPressedHandler == null) view.backPressedHandler = { }
      }

    return buildDialogWithContentView(contentViewHolder.view).also { dialog ->
      val window = requireNotNull(dialog.window) { "Dialog must be attached to a window." }

      // Stick the contentViewHolder in a tag, where updateDialog can find it later.
      window.peekDecorView()?.setTag(R.id.workflow_modal_dialog_content, contentViewHolder)
        ?: throw IllegalStateException("Expected decorView to have been built.")

      val realWindowCallback = window.callback
      window.callback = object : Window.Callback by realWindowCallback {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
          val isBackPress = (event.keyCode == KEYCODE_BACK || event.keyCode == KEYCODE_ESCAPE) &&
            event.action == ACTION_UP

          return when {
            isBackPress -> contentViewHolder.environment[ModalScreenOverlayOnBackPressed]
              .onBackPressed(contentViewHolder.view)
            else -> realWindowCallback.dispatchKeyEvent(event)
          }
        }
      }

      window.setFlags(FLAG_NOT_TOUCH_MODAL, FLAG_NOT_TOUCH_MODAL)
      dialog.maintainBounds(contentViewHolder.environment) { d, b -> updateBounds(d, Rect(b)) }
    }
  }

  final override fun updateDialog(
    dialog: Dialog,
    rendering: O,
    environment: ViewEnvironment
  ) {

    dialog.window?.peekDecorView()
      ?.let {
        @Suppress("UNCHECKED_CAST")
        it.getTag(R.id.workflow_modal_dialog_content) as? ScreenViewHolder<Screen>
      }
      ?.show(
        // Have to preserve the wrapping done in buildDialog.
        BackButtonScreen(rendering.content) { },
        environment
      )
  }
}

/**
 * The default implementation of [ScreenOverlayDialogFactory.buildDialogWithContentView].
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
