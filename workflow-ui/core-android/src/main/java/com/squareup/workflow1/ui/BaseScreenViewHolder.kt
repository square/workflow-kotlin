package com.squareup.workflow1.ui

import android.view.View

@WorkflowUiExperimentalApi
internal class BaseScreenViewHolder<ScreenT : Screen>(
  initialRendering: ScreenT,
  initialViewEnvironment: ViewEnvironment,
  override val view: View,
  private val updater: ScreenViewUpdater<ScreenT>
) : ScreenViewHolder<ScreenT> {
  private var currentRendering: ScreenT = initialRendering
  private var currentEnvironment: ViewEnvironment = initialViewEnvironment

  override val screen: ScreenT
    get() = currentRendering
  override val environment: ViewEnvironment
    get() = currentEnvironment

  override fun start() {
    showScreen(currentRendering, currentEnvironment)
  }

  override fun showScreen(screen: ScreenT, environment: ViewEnvironment) {
    currentRendering = screen
    currentEnvironment = environment
    updater.showRendering(screen, environment)
  }
}
