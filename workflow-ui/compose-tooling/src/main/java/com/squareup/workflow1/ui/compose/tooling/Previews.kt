package com.squareup.workflow1.ui.compose.tooling

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactoryFinder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.ScreenComposableFactory
import com.squareup.workflow1.ui.compose.ScreenComposableFactoryFinder
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.asComposableFactory

/**
 * Uses [ScreenComposableFactory.Preview] or [ScreenViewFactory.Preview]
 * to draw the receiving [Screen].
 *
 * Note that this function can preview any kind of [Screen], whether it's bound
 * to UI code implemented via Compose or classic [View][android.view.View] code.
 *
 * Use inside `@Preview` Composable functions:
 *
 *     @Preview(heightDp = 150, showBackground = true)
 *     @Composable
 *     fun HelloPreview() {
 *       HelloScreen(
 *         "Hello!",
 *         onClick = {}
 *       ).Preview()
 *     }
 */
@WorkflowUiExperimentalApi
@Composable
public fun Screen.Preview(
  modifier: Modifier = Modifier,
  placeholderModifier: Modifier = Modifier,
  viewEnvironmentUpdater: ((ViewEnvironment) -> ViewEnvironment)? = null
) {
  val factoryEnv = (
    viewEnvironmentUpdater?.invoke(ViewEnvironment.EMPTY)
      ?: ViewEnvironment.EMPTY
    )

  factoryEnv[ScreenComposableFactoryFinder]
    .getComposableFactoryForRendering(factoryEnv, this)
    ?.Preview(this, modifier, placeholderModifier, viewEnvironmentUpdater)?.also { return }

  factoryEnv[ScreenViewFactoryFinder]
    .getViewFactoryForRendering(factoryEnv, this)
    ?.Preview(this, modifier, placeholderModifier, viewEnvironmentUpdater)
}

/**
 * Draws this [ScreenComposableFactory] using a special preview [ScreenComposableFactoryFinder].
 *
 * Use inside `@Preview` Composable functions:
 *
 *     @Preview(heightDp = 150, showBackground = true)
 *     @Composable
 *     fun DrawHelloRenderingPreview() {
 *       HelloBinding.Preview(HelloScreen("Hello!", onClick = {}))
 *     }
 *
 * *Note: [rendering] must be the same type as this [ScreenComposableFactory], even though the type
 * system does not enforce this constraint. This is due to a Compose compiler bug tracked
 * [here](https://issuetracker.google.com/issues/156527332).*
 *
 * @param modifier [Modifier] that will be applied to this [ScreenComposableFactory].
 * @param placeholderModifier [Modifier] that will be applied to any nested renderings this factory
 * shows.
 * @param viewEnvironmentUpdater Function that configures the [ViewEnvironment] passed to this
 * factory.
 */
@WorkflowUiExperimentalApi
@Composable
public fun <RenderingT : Screen> ScreenComposableFactory<RenderingT>.Preview(
  rendering: RenderingT,
  modifier: Modifier = Modifier,
  placeholderModifier: Modifier = Modifier,
  viewEnvironmentUpdater: ((ViewEnvironment) -> ViewEnvironment)? = null
) {
  val previewEnvironment =
    rememberPreviewViewEnvironment(placeholderModifier, viewEnvironmentUpdater, mainFactory = this)
  WorkflowRendering(rendering, previewEnvironment, modifier)
}

/**
 * Like [ScreenComposableFactory.Preview], but for non-Compose [ScreenViewFactory] instances.
 * Yes, you can preview classic [View][android.view.View] code this way.
 *
 * Use inside `@Preview` Composable functions.
 *
 * *Note: [rendering] must be the same type as this [ScreenViewFactory], even though the type
 * system does not enforce this constraint. This is due to a Compose compiler bug tracked
 * [here](https://issuetracker.google.com/issues/156527332).*
 *
 * @param modifier [Modifier] that will be applied to this [ScreenViewFactory].
 * @param placeholderModifier [Modifier] that will be applied to any nested renderings this factory
 * shows.
 * @param viewEnvironmentUpdater Function that configures the [ViewEnvironment] passed to this
 * factory.
 */
@WorkflowUiExperimentalApi
@Composable
public fun <RenderingT : Screen> ScreenViewFactory<RenderingT>.Preview(
  rendering: RenderingT,
  modifier: Modifier = Modifier,
  placeholderModifier: Modifier = Modifier,
  viewEnvironmentUpdater: ((ViewEnvironment) -> ViewEnvironment)? = null
) {
  asComposableFactory().Preview(rendering, modifier, placeholderModifier, viewEnvironmentUpdater)
}
