package com.squareup.workflow1.ui.navigation

import android.app.Dialog
import android.graphics.Rect
import com.squareup.workflow1.ui.ViewEnvironment

internal class RealOverlayDialogHolder<OverlayT : Overlay>(
  initialEnvironment: ViewEnvironment,
  override val dialog: Dialog,
  override val onUpdateBounds: ((Rect) -> Unit)?,
  runnerFunction: (rendering: OverlayT, environment: ViewEnvironment) -> Unit
) : OverlayDialogHolder<OverlayT> {

  private var _environment: ViewEnvironment = initialEnvironment
  override val environment: ViewEnvironment get() = _environment

  override val runner = { newScreen: OverlayT, newEnvironment: ViewEnvironment ->
    _environment = newEnvironment
    runnerFunction(newScreen, newEnvironment)
  }
}
