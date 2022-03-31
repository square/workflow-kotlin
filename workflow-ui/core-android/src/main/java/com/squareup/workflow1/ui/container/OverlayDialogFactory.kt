package com.squareup.workflow1.ui.container

import android.app.Dialog
import android.content.Context
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Factory for [Dialog] instances that can show renderings of type [RenderingT] : [Overlay].
 *
 * It's simplest to have your rendering classes implement [AndroidOverlay] to associate
 * them with appropriate an appropriate [OverlayDialogFactory]. For more flexibility, and to
 * avoid coupling your workflow directly to the Android runtime, see [ViewRegistry].
 */
@WorkflowUiExperimentalApi
public interface OverlayDialogFactory<RenderingT : Overlay> : ViewRegistry.Entry<RenderingT> {
  /** Build a [Dialog], but do not show it. */
  public fun buildDialog(
    initialRendering: RenderingT,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): Dialog

  /**
   * Update a [dialog] previously built by [buildDialog] to reflect [rendering] and
   * [environment]. Bear in mind that this method may be called frequently, without
   * [rendering] or [environment] having changed from the previous call.
   */
  public fun updateDialog(
    dialog: Dialog,
    rendering: RenderingT,
    environment: ViewEnvironment
  )
}

@WorkflowUiExperimentalApi
public fun <T : Overlay> T.toDialogFactory(
  environment: ViewEnvironment
): OverlayDialogFactory<T> =
  environment[OverlayDialogFactoryFinder].getDialogFactoryForRendering(environment, this)
