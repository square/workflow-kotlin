package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Interface implemented by a rendering class to allow it to drive an Android UI
 * via an appropriate [OverlayDialogFactory] implementation.
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
