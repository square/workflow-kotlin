package com.squareup.workflow1.ui

import android.app.Dialog
import android.content.Context

/**
 * Factory for [Dialog]s that can show [ModalRendering]s of a particular [type][RenderingT].
 *
 * Sets of bindings are gathered in [ViewRegistry] instances.
 */
@WorkflowUiExperimentalApi
interface DialogBuilder<RenderingT : ModalRendering> : ViewRegistry.Entry<RenderingT> {
  /**
   * Returns a [Dialog] to display [initialRendering]. This method must call
   * [Dialog.bindDisplayFunction] on the new Dialog to display [initialRendering],
   * and to make the Dialog ready to respond to succeeding calls to [Dialog.display].
   */
  fun buildDialog(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    context: Context
  ): Dialog
}
