@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.container

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport.onBackPressedDispatcherOwner
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.setBackHandler
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.startShowing
import com.squareup.workflow1.ui.toViewFactory
import kotlin.reflect.KClass

@Deprecated("Use ComponentDialog.setContent")
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
      // For a modal, on each update put a no-op backHandler in place on the
      // decorView before updating, to ensure that the global androidx
      // OnBackPressedDispatcher doesn't fire any set by lower layers. We put this
      // in place before each call to show(), so the real content view will be able
      // to clobber it.
      if (modal) content.view.setBackHandler {}
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
      .startShowing(initialRendering.content, initialEnvironment, context) { view, doStart ->
        // Note that we never call destroyOnDetach for this owner. That's okay because
        // DialogSession.showNewDialog puts one in place above us on the decor view,
        // and cleans it up. It's in place by the time we attach to the window, and
        // so becomes our parent.
        WorkflowLifecycleOwner.installOn(
          view,
          initialEnvironment.onBackPressedDispatcherOwner(view)
        )
        doStart()
      }

    return buildDialogWithContent(
      initialRendering,
      initialEnvironment,
      contentViewHolder
    ).also { holder ->
      val window = requireNotNull(holder.dialog.window) { "Dialog must be attached to a window." }
      // Note that we always tell Android to make the window non-modal, regardless of our own
      // notion of its modality. Even a modal dialog should only block events within
      // the appropriate bounds, but Android makes them block everywhere.
      window.addFlags(FLAG_NOT_TOUCH_MODAL)
    }
  }
}

@Deprecated("Use the ComponentDialog.setContent extension instead.")
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
      // If we don't at least set it to null, the window cannot go full bleed.
      null
    }
  with(window!!) {
    setBackgroundDrawable(background)
    clearFlags(FLAG_DIM_BEHIND)
  }
}
