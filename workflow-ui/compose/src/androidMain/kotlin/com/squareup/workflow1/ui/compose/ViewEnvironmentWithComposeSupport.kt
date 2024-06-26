package com.squareup.workflow1.ui.compose

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactoryFinder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Replaces the [ScreenComposableFactoryFinder] and [ScreenViewFactoryFinder]
 * found in the receiving [ViewEnvironment] with wrappers that are able to
 * delegate from one platform to the other. Required to allow
 * [WorkflowViewStub][com.squareup.workflow1.ui.WorkflowViewStub]
 * to handle renderings bound to `@Composable` functions, and to allow
 * [WorkflowRendering] to handle renderings bound to [ScreenViewFactory].
 *
 * Note that the standard navigation related [Screen] types
 * (e.g. [BackStackScreen][com.squareup.workflow1.ui.navigation.BackStackScreen])
 * are mainly bound to [View][android.view.View]-based implementations.
 * Until that changes, effectively every Compose-based app must call this method.
 *
 * App-specific customizations of [ScreenComposableFactoryFinder] and [ScreenViewFactoryFinder]
 * must be placed in the [ViewEnvironment] before calling this method.
 */
@WorkflowUiExperimentalApi
public fun ViewEnvironment.withComposeInteropSupport(): ViewEnvironment {
  val rawViewFactoryFinder = get(ScreenViewFactoryFinder)
  val rawComposableFactoryFinder = get(ScreenComposableFactoryFinder)

  val convertingViewFactoryFinder = object : ScreenViewFactoryFinder {
    override fun <ScreenT : Screen> getViewFactoryForRendering(
      environment: ViewEnvironment,
      rendering: ScreenT
    ): ScreenViewFactory<ScreenT>? {
      return rawViewFactoryFinder.getViewFactoryForRendering(environment, rendering)
        ?: rawComposableFactoryFinder.getComposableFactoryForRendering(environment, rendering)
          ?.asViewFactory()
    }
  }

  val convertingComposableFactoryFinder = object : ScreenComposableFactoryFinder {
    override fun <ScreenT : Screen> getComposableFactoryForRendering(
      environment: ViewEnvironment,
      rendering: ScreenT
    ): ScreenComposableFactory<ScreenT>? {
      return rawComposableFactoryFinder.getComposableFactoryForRendering(environment, rendering)
        ?: rawViewFactoryFinder.getViewFactoryForRendering(environment, rendering)
          ?.asComposableFactory()
    }
  }

  return this + (ScreenViewFactoryFinder to convertingViewFactoryFinder) +
    (ScreenComposableFactoryFinder to convertingComposableFactoryFinder)
}
