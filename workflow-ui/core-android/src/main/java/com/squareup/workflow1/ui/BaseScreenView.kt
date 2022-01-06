package com.squareup.workflow1.ui

import android.view.View

@WorkflowUiExperimentalApi
internal class BaseScreenView<ScreenT: Screen>(
  private val initialRendering: ScreenT,
  private val initialViewEnvironment: ViewEnvironment,
  override val androidView: View,
  private val runner: ScreenViewRunner<ScreenT>
) : ScreenView<ScreenT> {
  lateinit var currentRendering: ScreenT
  lateinit var currentEnvironment: ViewEnvironment

  override val rendering: ScreenT
    get() = currentRendering
  override val environment: ViewEnvironment
    get() = currentEnvironment

  override fun start() {
    update(initialRendering, initialViewEnvironment)
  }

  override fun update(rendering: ScreenT, environment: ViewEnvironment) {
    currentRendering = rendering
    currentEnvironment = environment
    runner.showRendering(rendering, environment)
  }
}
