package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactoryFinder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Alternative to [WorkflowLayout][com.squareup.workflow1.ui.WorkflowLayout]
 * for a pure Compose application. Makes the receiver available via [LocalWorkflowEnvironment]
 * and runs the composition bound to [screen].
 *
 * Note that any app relying on stock navigation classes like
 * [BackStackScreen][com.squareup.workflow1.ui.navigation.BackStackScreen] or
 * [BodyAndOverlaysScreen][com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen]
 * are not pure Compose, and must call [ViewEnvironment.withComposeInteropSupport] first.
 */
@WorkflowUiExperimentalApi
@Composable public fun ViewEnvironment.RootScreen(
  screen: Screen,
  modifier: Modifier = Modifier
) {
  CompositionLocalProvider(LocalWorkflowEnvironment provides this) {
    WorkflowRendering(screen, modifier)
  }
}

/**
 * Replaces the [ScreenComposableFactoryFinder] and [ScreenViewFactoryFinder]
 * found in the receiving [ViewEnvironment] with wrappers that are able to
 * delegate from one platform to the other. Required to allow
 * [WorkflowViewStub][com.squareup.workflow1.ui.WorkflowViewStub]
 * to handle renderings bound to `@Composable` functions, and to allow
 * [WorkflowRendering] to handle renderings bound to [ScreenViewFactory].
 *
 * Note that the standard navigation [Screen] types
 * ([BackStackScreen][com.squareup.workflow1.ui.navigation.BackStackScreen] and
 * [BodyAndOverlaysScreen][com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen])
 * are bound to [View][android.view.View]-based implementations.
 * Until that changes, effectively every Compose-based app must call this method.
 *
 * App-specific customizations of [ScreenComposableFactoryFinder] and [ScreenViewFactoryFinder]
 * must be placed in the [ViewEnvironment] before calling this method.
 *
 * @param compositionRootOrNull optional [CompositionRoot] to be applied whenever
 * we create a Compose context, useful hook for applying
 * [composition locals][androidx.compose.runtime.CompositionLocal] that all
 * [ScreenComposableFactory] factories need access to, such as UI themes.
 */
@WorkflowUiExperimentalApi
public fun ViewEnvironment.withComposeInteropSupport(
  compositionRootOrNull: CompositionRoot? = null
): ViewEnvironment {
  val rawViewFactoryFinder = get(ScreenViewFactoryFinder)
  val rawComposableFactoryFinder = get(ScreenComposableFactoryFinder).let { finder ->
    compositionRootOrNull?.let { finder.withCompositionRoot(it) } ?: finder
  }

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
        ?: rawViewFactoryFinder
          .getViewFactoryForRendering(environment, rendering)?.asComposableFactory()
    }
  }

  return this + (ScreenViewFactoryFinder to convertingViewFactoryFinder) +
    (ScreenComposableFactoryFinder to convertingComposableFactoryFinder)
}
