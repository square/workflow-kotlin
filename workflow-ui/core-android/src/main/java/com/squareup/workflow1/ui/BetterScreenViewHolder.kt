package com.squareup.workflow1.ui

import android.view.View

@WorkflowUiExperimentalApi
public class BetterScreenViewHolder<ScreenT : Screen> private constructor(
  public val view: View,
  private val updater: ScreenViewUpdater<ScreenT>,
  internal val screenGetter: () -> ScreenT,
  internal val envGetter: () -> ViewEnvironment,
  internal val startWrapper: BetterScreenViewHolder<ScreenT>.(() -> Unit) -> Unit,
) {
  public constructor(
    view: View,
    updater: ScreenViewUpdater<ScreenT>,
    screen: ScreenT,
    env: ViewEnvironment
  ) : this(
    view = view,
    updater = updater,
    screenGetter = { screen },
    envGetter = { env },
    startWrapper = { doStart -> doStart() }
  )

  public val screen: ScreenT get() = screenGetter()
  public val environment: ViewEnvironment get() = envGetter()

  public fun start() {
    startWrapper.invoke(this) { updater.showRendering(screen, environment) }
  }

  public fun canShowScreen(screen: Screen): Boolean = compatible(this.screen, screen)

  public fun withStarter(
    startWrapper: BetterScreenViewHolder<ScreenT>.(() -> Unit) -> Unit
  ): BetterScreenViewHolder<ScreenT> {
    return BetterScreenViewHolder(view, updater, screenGetter, envGetter) { doStart ->
      startWrapper(doStart)
    }
  }

  public fun withShowScreen(
    
  )
}
