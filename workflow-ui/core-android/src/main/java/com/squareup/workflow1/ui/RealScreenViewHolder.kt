package com.squareup.workflow1.ui

import android.view.View

/**
 * Created by [ScreenViewFactory.start], a [ScreenViewHolder] holds a live
 * Android [View] driven by a workflow [ScreenT] rendering. It is rare
 * to use this class directly, [WorkflowViewStub] drives it and is more convenient.
 */
@WorkflowUiExperimentalApi
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
      viewRunner.showRendering(newScreen, newEnvironment)
    }
}
