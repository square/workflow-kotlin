package com.squareup.workflow1.ui.container

import android.app.Dialog
import android.content.Context
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Factory for [Dialog] instances that can show renderings of type [OverlayT] : [Overlay].
 *
 * It's simplest to have your rendering classes implement [AndroidOverlay] to associate
 * them with appropriate an appropriate [OverlayDialogFactory]. For more flexibility, and to
 * avoid coupling your workflow directly to the Android runtime, see [ViewRegistry].
 */
@WorkflowUiExperimentalApi
public interface OverlayDialogFactory<OverlayT : Overlay> : ViewRegistry.Entry<OverlayT> {
  /** Builds a [Dialog], but does not show it. */
  public fun buildDialog(
    initialRendering: OverlayT,
    initialEnvironment: ViewEnvironment,
    context: Context
  ): OverlayDialogHolder<OverlayT>
}

@WorkflowUiExperimentalApi
public fun <T : Overlay> T.toDialogFactory(
  environment: ViewEnvironment
): OverlayDialogFactory<T> =
  environment[OverlayDialogFactoryFinder].getDialogFactoryForRendering(environment, this)
