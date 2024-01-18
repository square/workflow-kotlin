package com.squareup.workflow1.ui.navigation

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import androidx.activity.ComponentDialog
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.OnBackPressedDispatcherOwnerKey
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.startShowing
import com.squareup.workflow1.ui.toViewFactory

/**
 * Given a [ComponentDialog], wrap it in an [OverlayDialogHolder] that can drive
 * the Dialog's content via instances of a particular type of [ScreenOverlay].
 * This is the most convenient way to implement [OverlayDialogFactory], see
 * the kdoc there for complete details.
 *
 * Dialogs managed this way are compatible with
 * [View.backPressedHandler][com.squareup.workflow1.ui.backPressedHandler],
 * and honor the [OverlayArea] and [CoveredByModal] values placed in
 * the [ViewEnvironment] by the standard [BodyAndOverlaysScreen] container.
 *
 * @param setContent the function that sets the view built for [C] as the
 * content of the [ComponentDialog] built for [O]. This is also a good hook
 * for configuring the newly made dialog. The default calls
 * [ComponentDialog.setContentView] and the [fixBackgroundAndDimming] extension
 * function provided below.
 */
@WorkflowUiExperimentalApi
public fun <C : Screen, O : ScreenOverlay<C>> ComponentDialog.asDialogHolderWithContent(
  overlay: O,
  environment: ViewEnvironment,
  setContent: (ScreenViewHolder<C>) -> Unit = { contentViewHolder ->
    setContentView(contentViewHolder.view)
    fixBackgroundAndDimming()
  }
): OverlayDialogHolder<O> {
  // Note that we always tell Android to make the window non-modal, regardless of our own
  // notion of its modality. Even a modal dialog should only block events within
  // the appropriate bounds, but Android makes them block everywhere by default.
  requireNotNull(window) { "Expected to find a window for $this." }.addFlags(FLAG_NOT_TOUCH_MODAL)

  val envWithOnBack = environment + (OnBackPressedDispatcherOwnerKey to this)
  val contentHolder = overlay.content.toViewFactory(envWithOnBack)
    .buildView(overlay.content, envWithOnBack, context)

  // We absolutely do not want Android to close the window behind our backs.
  // Feature devs should set back handlers in their content views if that's
  // what they want.
  setCancelable(false)

  setContent(contentHolder)

  contentHolder.startShowing(overlay.content, envWithOnBack) { view, doStart ->
    // Note that we never call destroyOnDetach for this owner. That's okay because
    // DialogSession.showNewDialog puts one in place above us on the decor view,
    // and cleans it up. It's in place by the time we attach to the window, and
    // so becomes our parent.
    WorkflowLifecycleOwner.installOn(view = view, onBackPressedDispatcherOwner = this)
    doStart()
  }

  // Note that we set onBackPressed to null, so that the implementation built
  // into ComponentDialog will be used. Our default implementation is a shabby
  // imitation of that one, and is going to be removed soon.
  return OverlayDialogHolder(
    initialEnvironment = environment,
    dialog = this,
    onBackPressed = null
  ) { newOverlay, newEnvironment ->
    contentHolder.show(
      newOverlay.content,
      newEnvironment + (OnBackPressedDispatcherOwnerKey to this@asDialogHolderWithContent)
    )
  }
}

/**
 * Called from the default `setContent` function of [asDialogHolderWithContent].
 * Fixes the default background and window flag settings that interfere with
 * making a [Dialog] respect the bounds required by [OverlayArea].
 */
public fun Dialog.fixBackgroundAndDimming() {
  // Welcome to Android. Nothing workflow-related here, this is just how one
  // finds the window background color for the theme.
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
