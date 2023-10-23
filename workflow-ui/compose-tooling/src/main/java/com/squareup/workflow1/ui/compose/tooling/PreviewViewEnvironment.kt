@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui.compose.tooling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactoryFinder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.ScreenComposableFactory
import com.squareup.workflow1.ui.compose.ScreenComposableFactoryFinder
import com.squareup.workflow1.ui.compose.asViewFactory

/**
 * Creates and [remember]s a [ViewEnvironment] that has a special [ScreenViewFactoryFinder]
 * and any additional elements as configured by [viewEnvironmentUpdater].
 *
 * The [ScreenViewFactoryFinder] will contain [mainFactory] if specified, as well as a
 * [placeholderScreenComposableFactory] that will be used to show any renderings that don't match
 * [mainFactory]'s type. All placeholders will have [placeholderModifier] applied.
 */
@Composable internal fun rememberPreviewViewEnvironment(
  placeholderModifier: Modifier,
  viewEnvironmentUpdater: ((ViewEnvironment) -> ViewEnvironment)? = null,
  mainFactory: ScreenComposableFactory<*>? = null
): ViewEnvironment {
  val composableFactoryFinder = remember(mainFactory, placeholderModifier) {
    PreviewScreenComposableFactoryFinder(
      mainFactory,
      placeholderScreenComposableFactory(placeholderModifier)
    )
  }
  val screenFactoryFinder = remember(mainFactory, placeholderModifier) {
    PreviewScreenViewFactoryFinder(
      mainFactory?.asViewFactory(),
      placeholderScreenComposableFactory(placeholderModifier).asViewFactory()
    )
  }
  return remember(composableFactoryFinder, viewEnvironmentUpdater) {
    (
      ViewEnvironment.EMPTY +
        (ScreenComposableFactoryFinder to composableFactoryFinder) +
        (ScreenViewFactoryFinder to screenFactoryFinder)
      ).let { environment ->
      // Give the preview a chance to add its own elements to the ViewEnvironment.
      viewEnvironmentUpdater?.let { it(environment) } ?: environment
    }
  }
}

/**
 * A [ScreenComposableFactoryFinder] that uses [mainFactory] for rendering [RenderingT]s,
 * and [placeholderFactory] for all other
 * [WorkflowRendering][com.squareup.workflow1.ui.compose.WorkflowRendering] calls.
 */
@Immutable
private class PreviewScreenComposableFactoryFinder<RenderingT : Screen>(
  private val mainFactory: ScreenComposableFactory<RenderingT>? = null,
  private val placeholderFactory: ScreenComposableFactory<Screen>
) : ScreenComposableFactoryFinder {
  @OptIn(WorkflowUiExperimentalApi::class)
  override fun <ScreenT : Screen> getComposableFactoryForRendering(
    environment: ViewEnvironment,
    rendering: ScreenT
  ): ScreenComposableFactory<ScreenT> =
    // This `isInstance()` check is a bit sketchy b/c the real code insists on
    // the types being exactly the same, but this is the easiest way to keep
    // ComposeScreen, AndroidScreen working.
    if (mainFactory?.type?.isInstance(rendering) == true) {
      @Suppress("UNCHECKED_CAST")
      mainFactory as ScreenComposableFactory<ScreenT>
    } else {
      placeholderFactory
    }
}

/**
 * [ScreenViewFactoryFinder] analog to [PreviewScreenComposableFactoryFinder].
 */
@Immutable
private class PreviewScreenViewFactoryFinder<RenderingT : Screen>(
  private val mainFactory: ScreenViewFactory<RenderingT>? = null,
  private val placeholderFactory: ScreenViewFactory<Screen>
) : ScreenViewFactoryFinder {
  override fun <ScreenT : Screen> getViewFactoryForRendering(
    environment: ViewEnvironment,
    rendering: ScreenT
  ): ScreenViewFactory<ScreenT> =
    @Suppress("UNCHECKED_CAST")
    if (rendering::class == mainFactory?.type) {
      mainFactory as ScreenViewFactory<ScreenT>
    } else {
      placeholderFactory
    }
}
