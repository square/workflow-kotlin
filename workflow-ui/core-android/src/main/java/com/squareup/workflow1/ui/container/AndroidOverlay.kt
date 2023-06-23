package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Interface implemented by a rendering class to allow it to drive an Android UI
 * via an appropriate [OverlayDialogFactory] implementation.
 *
 * Note that you can mix this interface with others:
 *
 * - [ModalOverlay] to indicate that user input to covered views and dialogs
 *   should be blocked while this one is visible.
 *
 * - [ScreenOverlay] for dialogs whose content is defined by a wrapped
 *   [Screen][com.squareup.workflow1.ui.Screen] instance. And in this case,
 *   also note the [ComponentDialog.setContent][setContent] extension function.
 *
 * For example:
 *
 *     data class MyModal<C : Screen>(
 *       override val content: C
 *     ) : ScreenOverlay<C>, ModalOverlay, AndroidOverlay<MyModal<C>> {
 *       override val dialogFactory = OverlayDialogFactory<MyModal<C>> { r, e, c ->
 *         AppCompatDialog(c).setContent(r, e)
 *       }
 *
 *       override fun <D : Screen> map(transform: (C) -> D) = MyModal(transform(content))
 *     }
 *
 * This is the simplest way to introduce a [Dialog][android.app.Dialog] workflow driven UI,
 * but using it requires your workflows code to reside in Android modules, instead
 * of pure Kotlin. If this is a problem, or you need more flexibility for any other
 * reason, you can use [ViewRegistry][com.squareup.workflow1.ui.ViewRegistry] to bind
 * your renderings to [OverlayDialogFactory] implementations at runtime.
 * Also note that a `ViewRegistry` entry will override the [dialogFactory] returned by
 * an [AndroidOverlay].
 *
 * @see com.squareup.workflow1.ui.AndroidScreen
 */
@WorkflowUiExperimentalApi
public interface AndroidOverlay<O : AndroidOverlay<O>> : Overlay {
  public val dialogFactory: OverlayDialogFactory<O>
}
