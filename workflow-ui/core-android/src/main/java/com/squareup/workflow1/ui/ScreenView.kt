package com.squareup.workflow1.ui

import android.view.View

@WorkflowUiExperimentalApi
public interface ScreenView<ScreenT : Screen> {
  public val rendering: ScreenT
  public val environment: ViewEnvironment
  public val androidView: View

  public fun start()

  public fun update(
    rendering: ScreenT,
    environment: ViewEnvironment
  )

  @WorkflowUiExperimentalApi
  public fun interface Starter<ScreenT : Screen> {
    /** Called from [start]. [doStart] must be invoked. */
    public fun startView(
      view: ScreenView<ScreenT>,
      doStart: () -> Unit
    )
  }
}

@WorkflowUiExperimentalApi
public fun ScreenView<*>.canShowRendering(screen: Screen): Boolean {
  return compatible(rendering, screen)
}

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenView<ScreenT>.withStarter(
  viewStarter: ScreenView.Starter<ScreenT>
): ScreenView<ScreenT> {
  return object : ScreenView<ScreenT> by this {
    override fun start() {
      viewStarter.startView(this@withStarter, this@withStarter::start)
    }
  }
}

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen, ScreenU : Screen> ScreenView<ScreenT>.mapRenderings(
  transform: (ScreenU) -> ScreenT
): ScreenView<ScreenU> {
  return object: ScreenView<ScreenU> {
    lateinit var unmapped: ScreenU

    override val rendering: ScreenU
      get() = unmapped

    override val environment: ViewEnvironment
      get() = this@mapRenderings.environment

    override val androidView: View
      get() = this@mapRenderings.androidView

    override fun start() {
      this@mapRenderings.start()
    }

    override fun update(rendering: ScreenU, environment: ViewEnvironment) {
      unmapped = rendering
      this@mapRenderings.update(transform(rendering), environment)
    }
  }
}

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenView<ScreenT>.mapEnvironment(
  updater: (ViewEnvironment) -> ViewEnvironment
): ScreenView<ScreenT>{
  return object: ScreenView<ScreenT> by this {
    override fun update(rendering: ScreenT, environment: ViewEnvironment) {
      this@mapEnvironment.update(rendering, updater(environment))
    }
  }
}
