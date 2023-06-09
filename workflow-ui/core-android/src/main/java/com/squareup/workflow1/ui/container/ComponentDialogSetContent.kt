package com.squareup.workflow1.ui.container

import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import androidx.activity.ComponentDialog
import com.squareup.workflow1.ui.OnBackPressedDispatcherOwnerKey
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.startShowing
import com.squareup.workflow1.ui.toViewFactory

/**
 * Given a [ComponentDialog], wrap it in an [OverlayDialogHolder] that can drive
 * the Dialog's content via instances of a particular type of [ScreenOverlay].
 *
 * Dialogs managed this way are compatible with
 * [View.backPressedHandler][com.squareup.workflow1.ui.backPressedHandler],
 * and honor the [OverlayArea] and [CoveredByModal] values placed in
 * the [ViewEnvironment] by the standard [BodyAndOverlaysScreen] container.
 */
@WorkflowUiExperimentalApi
public fun <C : Screen, O : ScreenOverlay<C>> ComponentDialog.setContent(
  overlay: O,
  environment: ViewEnvironment
): OverlayDialogHolder<O> {
  // Note that we always tell Android to make the window non-modal, regardless of our own
  // notion of its modality. Even a modal dialog should only block events within
  // the appropriate bounds, but Android makes them block everywhere.
  requireNotNull(window) { "Expected to find a window for $this." }.addFlags(FLAG_NOT_TOUCH_MODAL)

  val envWithOnBack = environment + (OnBackPressedDispatcherOwnerKey to this)
  val contentHolder = overlay.content.toViewFactory(envWithOnBack)
    .startShowing(overlay.content, envWithOnBack, context) { view, doStart ->
      // Note that we never call destroyOnDetach for this owner. That's okay because
      // DialogSession.showNewDialog puts one in place above us on the decor view,
      // and cleans it up. It's in place by the time we attach to the window, and
      // so becomes our parent.
      WorkflowLifecycleOwner.installOn(view = view, onBackPressedDispatcherOwner = this)
      doStart()
    }

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
      newEnvironment + (OnBackPressedDispatcherOwnerKey to this@setContent)
    )
  }
}
