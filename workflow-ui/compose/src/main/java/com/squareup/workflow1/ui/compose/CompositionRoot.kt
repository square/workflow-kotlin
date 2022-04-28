@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry", "DEPRECATION")

package com.squareup.workflow1.ui.compose

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactoryFinder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

/**
 * Used by [WrappedWithRootIfNecessary] to ensure the [CompositionRoot] is only applied once.
 */
private val LocalHasViewFactoryRootBeenApplied = staticCompositionLocalOf { false }

/**
 * A composable function that will be used to wrap the first (highest-level)
 * [composeScreenViewFactory] view factory in a composition. This can be used to setup any
 * [composition locals][androidx.compose.runtime.CompositionLocal] that all
 * [composeScreenViewFactory] factories need access to, such as UI themes.
 *
 * This function will called once, to wrap the _highest-level_ [composeScreenViewFactory]
 * in the tree. However, composition locals are propagated down to child [composeScreenViewFactory]
 * compositions, so any locals provided here will be available in _all_ [composeScreenViewFactory]
 * compositions.
 */
public typealias CompositionRoot = @Composable (content: @Composable () -> Unit) -> Unit

/**
 * Convenience function for applying a [CompositionRoot] to this [ViewEnvironment]'s
 * [ScreenViewFactoryFinder]. See [ScreenViewFactoryFinder.withCompositionRoot].
 */
@WorkflowUiExperimentalApi
public fun ViewEnvironment.withCompositionRoot(root: CompositionRoot): ViewEnvironment {
  return this +
    (ScreenViewFactoryFinder to this[ScreenViewFactoryFinder].withCompositionRoot(root)) +
    (ViewRegistry to this[ViewRegistry].withCompositionRoot(root))
}

/**
 * Returns a [ViewRegistry] that ensures that any [composeViewFactory] factories registered in this
 * registry will be wrapped exactly once with a [CompositionRoot] wrapper.
 * See [CompositionRoot] for more information.
 */
@WorkflowUiExperimentalApi
@Deprecated("Use ScreenViewFactoryFinder.withCompositionRoot")
public fun ViewRegistry.withCompositionRoot(root: CompositionRoot): ViewRegistry =
  mapFactories { factory ->
    @Suppress("UNCHECKED_CAST")
    (factory as? ComposeViewFactory<Any>)?.let { composeFactory ->
      @Suppress("UNCHECKED_CAST")
      composeViewFactory(composeFactory.type) { rendering, environment ->
        WrappedWithRootIfNecessary(root) {
          composeFactory.Content(rendering, environment)
        }
      }
    } ?: factory
  }

/**
 * Returns a [ScreenViewFactoryFinder] that ensures that any [composeScreenViewFactory]
 * factories registered in this registry will be wrapped exactly once with a [CompositionRoot]
 * wrapper. See [CompositionRoot] for more information.
 */
@WorkflowUiExperimentalApi
public fun ScreenViewFactoryFinder.withCompositionRoot(
  root: CompositionRoot
): ScreenViewFactoryFinder =
  mapFactories { factory ->
    @Suppress("UNCHECKED_CAST")
    (factory as? ComposeScreenViewFactory<Screen>)?.let { composeFactory ->
      @Suppress("UNCHECKED_CAST")
      composeScreenViewFactory(composeFactory.type) { rendering, environment ->
        WrappedWithRootIfNecessary(root) { composeFactory.Content(rendering, environment) }
      }
    } ?: factory
  }

/**
 * Adds [content] to the composition, ensuring that [CompositionRoot] has been applied. Will only
 * wrap the content at the highest occurrence of this function in the composition subtree.
 */
@VisibleForTesting(otherwise = PRIVATE)
@Composable internal fun WrappedWithRootIfNecessary(
  root: CompositionRoot,
  content: @Composable () -> Unit
) {
  if (LocalHasViewFactoryRootBeenApplied.current) {
    // The only way this local can have the value true is if, somewhere above this point in the
    // composition, the else case below was hit and wrapped us in the local. Since the root
    // wrapper will have already been applied, we can just compose content directly.
    content()
  } else {
    // If the local is false, this is the first time this function has appeared in the composition
    // so far. We provide a true value for the local for everything below us, so any recursive
    // calls to this function will hit the if case above and not re-apply the wrapper.
    CompositionLocalProvider(LocalHasViewFactoryRootBeenApplied provides true) {
      root(content)
    }
  }
}

/**
 * Applies [transform] to each [ViewFactory] in this registry. Transformations are applied lazily,
 * at the time of lookup via [ViewRegistry.getEntryFor].
 */
@WorkflowUiExperimentalApi
@Deprecated("Use ScreenViewFactoryFinder.mapFactories")
private fun ViewRegistry.mapFactories(
  transform: (ViewFactory<*>) -> ViewFactory<*>
): ViewRegistry = object : ViewRegistry {
  override val keys: Set<KClass<*>> get() = this@mapFactories.keys

  override fun <RenderingT : Any> getEntryFor(
    renderingType: KClass<out RenderingT>
  ): ViewRegistry.Entry<RenderingT>? {
    val rawEntry = this@mapFactories.getEntryFor(renderingType)
    val asViewFactory = (rawEntry as? ViewFactory<*>) ?: return rawEntry

    val transformedFactory = transform(asViewFactory)
    check(transformedFactory.type == renderingType) {
      "Expected transform to return a ViewFactory that is compatible with $renderingType, " +
        "but got one with type ${transformedFactory.type}"
    }
    @Suppress("UNCHECKED_CAST")
    return transformedFactory as ViewFactory<RenderingT>
  }
}

@WorkflowUiExperimentalApi
private fun ScreenViewFactoryFinder.mapFactories(
  transform: (ScreenViewFactory<*>) -> ScreenViewFactory<*>
): ScreenViewFactoryFinder = object : ScreenViewFactoryFinder {
  override fun <ScreenT : Screen> getViewFactoryForRendering(
    environment: ViewEnvironment,
    rendering: ScreenT
  ): ScreenViewFactory<ScreenT> {
    val factoryFor = this@mapFactories.getViewFactoryForRendering(environment, rendering)
    val transformedFactory = transform(factoryFor)
    check(transformedFactory.type == rendering::class) {
      "Expected transform to return a ScreenViewFactory that is compatible " +
        "with ${rendering::class}, but got one with type ${transformedFactory.type}"
    }
    @Suppress("UNCHECKED_CAST")
    return transformedFactory as ScreenViewFactory<ScreenT>
  }
}
