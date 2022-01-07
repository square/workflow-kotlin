package com.squareup.workflow1.ui

import android.view.View

/**
 * Wraps an [Android view][view] with:
 *
 * - the current [Screen] [screen] it's displaying
 * - the [environment] that [screen] was shown with
 * - a [showScreen] method to refresh it
 *
 * Instances are created via [ScreenViewFactory.buildView]. [start] must be called
 * exactly once before [showScreen], to initialize the new view. Use [withStarter] to
 * customize what the [start] method does.
 */
@WorkflowUiExperimentalApi
public interface ScreenViewHolder<ScreenT : Screen> {
  public val screen: ScreenT
  public val environment: ViewEnvironment
  public val view: View

  public fun start()

  public fun showScreen(
    screen: ScreenT,
    environment: ViewEnvironment
  )

  @WorkflowUiExperimentalApi
  public fun interface Starter<ScreenT : Screen> {
    /** Called from [start]. [doStart] must be invoked. */
    public fun startView(
      viewHolder: ScreenViewHolder<ScreenT>,
      doStart: () -> Unit
    )
  }
}

/** Wraps [view] in a [ScreenViewHolder], mainly for use from [ManualScreenViewFactory]. */
@Suppress("FunctionName")
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewHolder(
  initialRendering: ScreenT,
  initialViewEnvironment: ViewEnvironment,
  view: View,
  updater: ScreenViewUpdater<ScreenT>
): ScreenViewHolder<ScreenT> {
  return BaseScreenViewHolder(initialRendering, initialViewEnvironment, view, updater)
}

@WorkflowUiExperimentalApi
public fun ScreenViewHolder<*>.canShowScreen(screen: Screen): Boolean {
  return compatible(this.screen, screen)
}

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewHolder<ScreenT>.withStarter(
  viewStarter: ScreenViewHolder.Starter<ScreenT>
): ScreenViewHolder<ScreenT> {
  return object : ScreenViewHolder<ScreenT> by this {
    override fun start() {
      viewStarter.startView(this@withStarter, this@withStarter::start)
    }
  }
}

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen, ScreenU : Screen> ScreenViewHolder<ScreenT>.mapRenderings(
  transform: (ScreenU) -> ScreenT
): ScreenViewHolder<ScreenU> {
  return object: ScreenViewHolder<ScreenU> {
    lateinit var unmapped: ScreenU

    override val screen: ScreenU
      get() = unmapped

    override val environment: ViewEnvironment
      get() = this@mapRenderings.environment

    override val view: View
      get() = this@mapRenderings.view

    override fun start() {
      this@mapRenderings.start()
    }

    override fun showScreen(screen: ScreenU, environment: ViewEnvironment) {
      unmapped = screen
      this@mapRenderings.showScreen(transform(screen), environment)
    }
  }
}

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewHolder<ScreenT>.mapEnvironment(
  updater: (ViewEnvironment) -> ViewEnvironment
): ScreenViewHolder<ScreenT>{
  return object: ScreenViewHolder<ScreenT> by this {
    override fun showScreen(screen: ScreenT, environment: ViewEnvironment) {
      this@mapEnvironment.showScreen(screen, updater(environment))
    }
  }
}
