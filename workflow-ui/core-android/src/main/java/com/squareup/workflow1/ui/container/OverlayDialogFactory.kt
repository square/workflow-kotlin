package com.squareup.workflow1.ui.container

import android.app.Dialog
import android.content.Context
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Factory for [Dialog] instances that can show renderings of type [OverlayT] : [Overlay].
 * See [setContent] for ease of implementing [ScreenOverlay] factories.
 *
 * To minimize boilerplate, have your rendering classes implement [AndroidOverlay] to associate
 * them with appropriate an appropriate [OverlayDialogFactory]. For more flexibility, and to
 * avoid coupling your workflow directly to the Android runtime, see [ViewRegistry].
 *
 * ## Details of [Dialog] management
 *
 * There is a lot of machinery provided to give control over the placement of
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
 * The default container view driven by [BodyAndOverlaysScreen] automatically honors
 * [CoveredByModal].
 *
 * All of this is driven by the [LayeredDialogSessions] support class, which can also be used to
 * create custom [BodyAndOverlaysScreen] container views. To put such a custom container
 * in play, see [OverlayDialogFactoryFinder].
 *
 * It is important to note that the modal behavior described here is all keyed to the
 * [ModalOverlay] interface, not its parent type [Overlay]. Rendering types that declare the
 * latter but not the former can be used to create dialogs for non-modal windows like toasts
 * and tool tips.
 */
@WorkflowUiExperimentalApi
public interface OverlayDialogFactory<OverlayT : Overlay> : ViewRegistry.Entry<OverlayT> {
  /** Builds a [Dialog], but does not show it. */
  public fun buildDialog(
    initialRendering: OverlayT,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): OverlayDialogHolder<OverlayT>

  public companion object {
    public inline operator fun <reified OverlayT : Overlay> invoke(
      crossinline buildDialog: (
        initialRendering: OverlayT,
        initialEnvironment: ViewEnvironment,
        context: Context
      ) -> OverlayDialogHolder<OverlayT>
    ): OverlayDialogFactory<OverlayT> {
      return object : OverlayDialogFactory<OverlayT> {
        override val type = OverlayT::class

        override fun buildDialog(
          initialRendering: OverlayT,
          initialEnvironment: ViewEnvironment,
          context: Context
        ): OverlayDialogHolder<OverlayT> =
          buildDialog(initialRendering, initialEnvironment, context)
      }
    }
  }
}

/**
 * Use the [OverlayDialogFactory] in [environment] to return the [OverlayDialogFactory] bound to the
 * type of the receiving [Overlay].
 *
 * It is rare to call this method directly. Instead the most common path is to rely on
 * the default container `View` bound to [BodyAndOverlaysScreen]. If you need to build
 * your own replacement for that `View`, you should be able to delegate most of the
 * work to [LayeredDialogSessions], which will call this method for you. And see
 * [OverlayDialogFactoryFinder] to change the default binding [BodyAndOverlaysScreen]
 * to your custom `View`.
 */
@WorkflowUiExperimentalApi
public fun <T : Overlay> T.toDialogFactory(
  environment: ViewEnvironment
): OverlayDialogFactory<T> =
  environment[OverlayDialogFactoryFinder].getDialogFactoryForRendering(environment, this)
