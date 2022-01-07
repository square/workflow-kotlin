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

/** Wraps [view] in a [ScreenViewHolder], mainly for use from [ScreenViewFactory.of]. */
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

/**
 * @param onShowScreen Function to be called in place of the receiver's
 * [ScreenViewHolder.showScreen] method. When invoked, `this` is the
 * original [ScreenViewHolder] that received the [withShowScreen] call,
 * so [onShowScreen] has access to the original [ScreenViewHolder.showScreen] method.
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewHolder<ScreenT>.withShowScreen(
  onShowScreen: ScreenViewHolder<ScreenT>.(ScreenT, ViewEnvironment) -> Unit
): ScreenViewHolder<ScreenT> {
  return object : ScreenViewHolder<ScreenT> by this {
    override fun showScreen(screen: ScreenT, environment: ViewEnvironment) {
      this@withShowScreen.onShowScreen(screen, environment)
    }
  }
}

/**
 * Transforms the [ScreenViewHolder.showScreen] method of the receiver to accept [NewS]
 * instead of [OriginalS], by applying the [given function][transform] to convert
 * [NewS] to [OriginalS].
 */
@WorkflowUiExperimentalApi
public fun <OriginalS : Screen, NewS : Screen> ScreenViewHolder<OriginalS>.acceptRenderings(
  initialRendering: NewS,
  transform: (NewS) -> OriginalS
): ScreenViewHolder<NewS> {
  return object : ScreenViewHolder<NewS> {
    var untransformed: NewS = initialRendering

    override val screen: NewS
      get() = untransformed

    override val environment: ViewEnvironment
      get() = this@acceptRenderings.environment

    override val view: View
      get() = this@acceptRenderings.view

    override fun start() {
      this@acceptRenderings.start()
    }

    override fun showScreen(screen: NewS, environment: ViewEnvironment) {
      untransformed = screen
      this@acceptRenderings.showScreen(transform(screen), environment)
    }
  }
}

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewHolder<ScreenT>.updateEnvironment(
  updater: (ViewEnvironment) -> ViewEnvironment
): ScreenViewHolder<ScreenT> {
  return object : ScreenViewHolder<ScreenT> by this {
    override fun showScreen(screen: ScreenT, environment: ViewEnvironment) {
      this@updateEnvironment.showScreen(screen, updater(environment))
    }
  }
}
