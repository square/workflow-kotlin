package com.squareup.workflow1.ui.container

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Window
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
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
 * (Use a custom [OverlayDialogFactoryFinder] to customize the presentation
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
 * the case, override [buildDialogWithContent] and provide an alternative `onBackPressed`
 * implementation when you call [OverlayDialogHolder].
 *
 * It is important to note that the modal behavior described here is all keyed to the
 * [ModalOverlay] interface, not its parent type [Overlay]. Rendering types that declare the
 * latter but not the former can be used to create dialogs for non-modal windows like toasts
 * and tool tips.
 */
@WorkflowUiExperimentalApi
public open class ScreenOverlayDialogFactory<S : Screen, O : ScreenOverlay<S>>(
  override val type: KClass<in O>
) : OverlayDialogFactory<O> {
  /**
   * Build the [Dialog], using [content] as its [contentView][Dialog.setContentView].
   * Open to allow customization, typically theming. Subclasses need not call `super`.
   *  - Note that the default implementation calls the provided [Dialog.setContent]
   *    extension for typical setup.
   *  - Be sure to call [ScreenViewHolder.show] from [OverlayDialogHolder.runner].
   */
  public open fun buildDialogWithContent(
    initialRendering: O,
    initialEnvironment: ViewEnvironment,
    content: ScreenViewHolder<S>
  ): OverlayDialogHolder<O> {
    val dialog = Dialog(content.view.context).apply { setContent(content) }
    val modal = initialRendering is ModalOverlay

    return OverlayDialogHolder(initialEnvironment, dialog) { overlayRendering, environment ->
      // For a modal, on each update put a no-op backPressedHandler in place on the
      // decorView before updating, to ensure that the global androidx
      // OnBackPressedDispatcher doesn't fire any set by lower layers. We put this
      // in place before each call to show(), so the real content view will be able
      // to clobber it.
      if (modal) content.view.backPressedHandler = {}
      content.show(overlayRendering.content, environment)
    }
  }

  /**
   * Creates the [ScreenViewHolder] for [initialRendering.content][ScreenOverlay.content]
   * and then calls [buildDialogWithContent] to create [Dialog] in an [OverlayDialogHolder].
   */
  final override fun buildDialog(
    initialRendering: O,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): OverlayDialogHolder<O> {
    val contentViewHolder = initialRendering.content.toViewFactory(initialEnvironment)
      .startShowing(initialRendering.content, initialEnvironment, context)

    return buildDialogWithContent(
      initialRendering,
      initialEnvironment,
      contentViewHolder
    ).also { holder ->
      val window = requireNotNull(holder.dialog.window) { "Dialog must be attached to a window." }
      // Note that we always tell Android to make the window non-modal, regardless of our own
      // notion of its modality. Even a modal dialog should only block events within
      // the appropriate bounds, but Android makes them block everywhere.
      window.setFlags(FLAG_NOT_TOUCH_MODAL, FLAG_NOT_TOUCH_MODAL)
    }
  }
}

/**
 * The default implementation of [ScreenOverlayDialogFactory.buildDialogWithContent].
 *
 * - Makes the receiver [non-cancelable][Dialog.setCancelable]
 *
 * - Sets the [background][Window.setBackgroundDrawable] of the receiver's [Window] based
 *   on its theme, if any, or else `null`. (Setting the background to `null` ensures the window
 *   can go full bleed.)
 */
@OptIn(WorkflowUiExperimentalApi::class)
public fun Dialog.setContent(contentHolder: ScreenViewHolder<*>) {
  setCancelable(false)
  setContentView(contentHolder.view)

  // Welcome to Android. Nothing workflow-related here, this is just how one
  // finds the window background color for the theme. I sure hope it's better in Compose.
  val maybeWindowColor = TypedValue()
  context.theme.resolveAttribute(android.R.attr.windowBackground, maybeWindowColor, true)

  val background =
    if (maybeWindowColor.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
      ColorDrawable(maybeWindowColor.data)
    } else {
      ColorDrawable(contentHolder.view.resources.getColor(android.R.color.white))
    }
  window!!.setBackgroundDrawable(background)
}
