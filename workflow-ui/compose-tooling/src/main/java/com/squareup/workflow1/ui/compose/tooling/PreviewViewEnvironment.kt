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

/**
 * Creates and [remember]s a [ViewEnvironment] that has a special [ScreenViewFactoryFinder]
 * and any additional elements as configured by [viewEnvironmentUpdater].
 *
 * The [ScreenViewFactoryFinder] will contain [mainFactory] if specified, as well as a
 * [placeholderScreenViewFactory] that will be used to show any renderings that don't match
 * [mainFactory]'s type. All placeholders will have [placeholderModifier] applied.
 */
@Composable internal fun rememberPreviewViewEnvironment(
  placeholderModifier: Modifier,
  viewEnvironmentUpdater: ((ViewEnvironment) -> ViewEnvironment)? = null,
  mainFactory: ScreenViewFactory<*>? = null
): ViewEnvironment {
  val finder = remember(mainFactory, placeholderModifier) {
    PreviewScreenViewFactoryFinder(mainFactory, placeholderScreenViewFactory(placeholderModifier))
  }
  return remember(finder, viewEnvironmentUpdater) {
    (ViewEnvironment.EMPTY + (ScreenViewFactoryFinder to finder)).let { environment ->
      // Give the preview a chance to add its own elements to the ViewEnvironment.
      viewEnvironmentUpdater?.let { it(environment) } ?: environment
    }
  }
}

/**
 * A [ScreenViewFactoryFinder] that uses [mainFactory] for rendering [RenderingT]s,
 * and [placeholderFactory] for all other
 * [WorkflowRendering][com.squareup.workflow1.ui.compose.WorkflowRendering] calls.
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
