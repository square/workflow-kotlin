package com.squareup.workflow1.ui

import android.view.View

internal class RealScreenViewHolder<ScreenT : Screen>(
  initialEnvironment: ViewEnvironment,
  override val view: View,
  viewRunner: ScreenViewRunner<ScreenT>
) : ScreenViewHolder<ScreenT> {

  private var _environment: ViewEnvironment = initialEnvironment
  override val environment: ViewEnvironment get() = _environment

  override val runner: ScreenViewRunner<ScreenT> =
    ScreenViewRunner { newScreen, newEnvironment ->
      _environment = newEnvironment
      view.setTag(R.id.workflow_environment, newEnvironment)
      viewRunner.showRendering(newScreen, newEnvironment)
    }
}
