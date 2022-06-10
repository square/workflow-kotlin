package com.squareup.workflow1.ui.container

import android.app.Dialog
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
internal class RealOverlayDialogHolder<OverlayT : Overlay>(
  initialEnvironment: ViewEnvironment,
  override val dialog: Dialog,
  dialogRunner: OverlayDialogRunner<OverlayT>
) : OverlayDialogHolder<OverlayT> {

  private var _environment: ViewEnvironment = initialEnvironment
  override val environment: ViewEnvironment get() = _environment

  override val runner: OverlayDialogRunner<OverlayT> =
    OverlayDialogRunner { newScreen, newEnvironment ->
      _environment = newEnvironment
      dialogRunner.showRendering(newScreen, newEnvironment)
    }
}
