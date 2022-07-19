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
import android.view.Window
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler
import com.squareup.workflow1.ui.environmentOrNull
import kotlin.reflect.KClass

/**
 * Extensible base implementation of [OverlayDialogFactory] for [ScreenOverlay]
 * types. Also serves as the default factory for [FullScreenOverlay].
 * (See [OverlayDialogFactoryFinder] for guidance on customizing the presentation
 * of [FullScreenOverlay].)
 *
 * Dialogs built by this class are compatible with
 * [View.backPressedHandler][com.squareup.workflow1.ui.backPressedHandler],
 * and honor the [OverlayArea] and [CoveredByModal] values placed in
 * the [ViewEnvironment] by the standard [BodyAndOverlaysScreen] container.
 *
 * ## Details of [Dialog] management
 *
 * There is a lot of machinery in play to to provide control over the placement of
 * [Dialog] windows, and to ensure that [ModalOverlay] dialogs behave as expected (i.e.,
 * that events in the Activity window are blocked the instant a modal [Dialog] is shown).
 *
 * For placement, consider a layout where we want the option to show a tutorial bar below
 * the main UI.
 *
 *    +-------------------------+
 *    |                         |
 *    |  BodyAndOverlaysScreen  |
 *    |                         |
 *    +-------------------------+
 *    |    TutorialBarScreen    |
 *    +-------------------------+
 *
 * Suppose we have custom dialogs that we want to cover the entire screen, except when
 * tutorial is running -- the tutorial bar should always be visible, and should always
 * be able to field touch events.
 *
 * To support this case we provide the [OverlayArea] value in the [ViewEnvironment].
 * When a [BodyAndOverlaysScreen] includes [overlays][BodyAndOverlaysScreen.overlays],
 * the [OverlayArea] holds the bounds of the view created to display the
 * [body screen][BodyAndOverlaysScreen.body]. Well behaved dialogs created to
 * display those [Overlay] renderings look for [OverlayArea] value and restrict
 * themselves to the reported bounds.
 *
 * Dialogs created via [ScreenOverlayDialogFactory] implementations honor [OverlayArea]
 * automatically. [updateBounds] is called as the [OverlayArea] changes, and the
 * default implementation of that method sets created dialog windows to fill the given area --
 * not necessarily the entire display.
 *
 * Another [ViewEnvironment] value is maintained to support modality: [CoveredByModal].
 * When this value is true, it indicates that a dialog window driven by a [ModalOverlay]
 * is in play over the view, or is about to be, and so touch and click events should be
 * ignored. This is necessary because there is a long period after a call to
 * [Dialog.show][android.app.Dialog.show] before the new Dialog window will start
 * intercepting events -- an issue that has preoccupied Square for a
 * [very long time](https://stackoverflow.com/questions/2886407/dealing-with-rapid-tapping-on-buttons).)
 *
 * The default container view driven by [BodyAndOverlaysScreen] automatically honors
 * [CoveredByModal]. Dialog windows built by [ScreenOverlayDialogFactory] also honor
 * [CoveredByModal] when there is a [ModalOverlay]-driven dialog with a higher Z index in play.
 * All of this is driven by the [LayeredDialogSessions] support class, which can also be used to
 * create custom [BodyAndOverlaysScreen] container views. To put such a custom container
 * in play, see [OverlayDialogFactoryFinder].
 *
 * Modality also has implications for the handling of the Android back button.
 * While a modal dialog is shown,
 * [back press handlers][com.squareup.workflow1.ui.backPressedHandler] on covered
 * views and windows should not fire. [ScreenOverlayDialogFactory] takes care
 * of that requirement by default, presuming that your app uses Jetpack
 * [OnBackPressedDispatcher][androidx.activity.OnBackPressedDispatcher]. If that is not
 * the case, alternative handling can be provided via [ModalScreenOverlayBackButtonHelper].
 *
 * It is important to note that the modal behavior described here is all is keyed to the
 * [ModalOverlay] interface, not its parent type [Overlay]. Rendering types that declare the
 * latter but not the former can be used to create dialogs for non-modal windows like toasts
 * and tool tips.
 */
@WorkflowUiExperimentalApi
public open class ScreenOverlayDialogFactory<S : Screen, O : ScreenOverlay<S>>(
  override val type: KClass<in O>
) : OverlayDialogFactory<O> {

  /**
   * Called from [buildDialog] to create (but not [show][Dialog.show]) a [Dialog] instance.
   * Open to allow customization, typically theming.
   *
   * The default implementation delegates all work beyond instantiation to the
   * provided [Dialog.setContent] extension function. Subclasses need not call `super`.
   */
  public open fun buildDialogWithContent(
    content: WorkflowLayout,
    environment: ViewEnvironment
  ): Dialog {
    return Dialog(content.context).also { it.setContent(content) }
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
    val modal = initialRendering is ModalOverlay

    val workflowLayout = WorkflowLayout(context)

    return buildDialogWithContent(workflowLayout, initialEnvironment).let { dialog ->
      val window = requireNotNull(dialog.window) { "Dialog must be attached to a window." }

      if (modal) {
        val realWindowCallback = window.callback
        window.callback = object : Window.Callback by realWindowCallback {
          override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val isBackPress = with(event) {
              (keyCode == KEYCODE_BACK || keyCode == KEYCODE_ESCAPE) && action == ACTION_UP
            }

            return when {
              isBackPress ->
                workflowLayout.environmentOrNull?.get(ModalScreenOverlayBackButtonHelper)
                  ?.onBackPressed(workflowLayout) == true
              else -> realWindowCallback.dispatchKeyEvent(event)
            }
          }
        }
      }

      // Note that we always tell Android to make the window non-modal, regardless of our own
      // notion of its modality. Even a modal dialog should only block events within
      // the appropriate bounds, but Android makes them block everywhere.
      window.setFlags(FLAG_NOT_TOUCH_MODAL, FLAG_NOT_TOUCH_MODAL)

      // Keep an eye on the bounds StateFlow(Rect) put in place by [LayeredDialogSessions].
      dialog.maintainBounds(initialEnvironment) { d, b -> updateBounds(d, Rect(b)) }

      OverlayDialogHolder(initialEnvironment, dialog) { overlayRendering, environment ->
        // Ensure that any androidx handlers that were put in play before this modal window
        // was updated are blocked.
        if (modal) workflowLayout.backPressedHandler = {}
        workflowLayout.show(overlayRendering.content, environment)
      }
    }
  }
}

/**
 * Post-processing applied by the default implementation of
 * [ScreenOverlayDialogFactory.buildDialogWithContent], provided as
 * an optional convenience for custom overrides of that method.
 *
 * - Makes the receiver [non-cancelable][Dialog.setCancelable]
 *
 * - Sets the [background][Window.setBackgroundDrawable] of the receiver's [Window] based
 *   on its theme, if any, or else `null`. (Setting the background to `null` ensures the window
 *   can go full bleed.)
 */
@OptIn(WorkflowUiExperimentalApi::class)
public fun Dialog.setContent(content: WorkflowLayout) {
  setCancelable(false)
  setContentView(content)

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
