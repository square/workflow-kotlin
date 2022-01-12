@file:Suppress("DEPRECATION")
@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.workflow1.ui.compose.tooling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

/**
 * Creates and [remember]s a [ViewEnvironment] that has a special [ViewRegistry] and any additional
 * elements as configured by [viewEnvironmentUpdater].
 *
 * The [ViewRegistry] will contain [mainFactory] if specified, as well as a [placeholderViewFactory]
 * that will be used to show any renderings that don't match [mainFactory]'s type. All placeholders
 * will have [placeholderModifier] applied.
 */
@Composable internal fun previewViewEnvironment(
  placeholderModifier: Modifier,
  viewEnvironmentUpdater: ((ViewEnvironment) -> ViewEnvironment)? = null,
  mainFactory: ViewFactory<*>? = null
): ViewEnvironment {
  val viewRegistry = remember(mainFactory, placeholderModifier) {
    PreviewViewRegistry(mainFactory, placeholderViewFactory(placeholderModifier))
  }
  return remember(viewRegistry, viewEnvironmentUpdater) {
    ViewEnvironment(mapOf(ViewRegistry to viewRegistry)).let { environment ->
      // Give the preview a chance to add its own elements to the ViewEnvironment.
      viewEnvironmentUpdater?.let { it(environment) } ?: environment
    }
  }
}

/**
 * A [ViewRegistry] that uses [mainFactory] for rendering [RenderingT]s, and [placeholderFactory]
 * for all other [WorkflowRendering][com.squareup.workflow.ui.compose.WorkflowRendering] calls.
 */
@Immutable
private class PreviewViewRegistry<RenderingT : Any>(
  private val mainFactory: ViewFactory<RenderingT>? = null,
  private val placeholderFactory: ViewFactory<Any>
) : ViewRegistry {
  override val keys: Set<KClass<*>> get() = mainFactory?.let { setOf(it.type) } ?: emptySet()

  @Suppress("UNCHECKED_CAST")
  override fun <RenderingT : Any> getEntryFor(
    renderingType: KClass<out RenderingT>
  ): ViewFactory<RenderingT> = when (renderingType) {
    mainFactory?.type -> mainFactory
    else -> placeholderFactory
  } as ViewFactory<RenderingT>
}
