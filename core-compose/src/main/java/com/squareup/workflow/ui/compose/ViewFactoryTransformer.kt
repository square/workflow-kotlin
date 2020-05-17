@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.workflow.ui.compose

import androidx.compose.Composable
import androidx.compose.remember
import androidx.ui.core.Modifier
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewEnvironmentKey
import com.squareup.workflow.ui.ViewFactory
import com.squareup.workflow.ui.ViewRegistry
import com.squareup.workflow.ui.compose.internal.showRendering
import kotlin.reflect.KClass

/**
 * TODO write documentation
 */
interface ViewFactoryTransformer {
  @Composable() fun modifyView(
    renderingDepth: Int,
    viewEnvironment: ViewEnvironment
  ): Modifier
}

/**
 * TODO kdoc
 */
fun ViewRegistry.modifyViewFactories(transformer: ViewFactoryTransformer): ViewRegistry =
  TransformedViewRegistry(this, transformer)

private class TransformedViewRegistry(
  private val delegate: ViewRegistry,
  private val transformer: ViewFactoryTransformer
) : ViewRegistry {
  override val keys: Set<KClass<*>> = delegate.keys

  override fun <RenderingT : Any> getFactoryFor(
    renderingType: KClass<out RenderingT>
  ): ViewFactory<RenderingT> {
    @Suppress("UNCHECKED_CAST")
    val realFactory = delegate.getFactoryFor(renderingType) as ViewFactory<Any>

    @Suppress("UNCHECKED_CAST")
    return ComposeViewFactory(renderingType as KClass<RenderingT>) { rendering, environment ->
      // No need to key depth on the environment, the depth will never change.
      val depth = remember { environment[FactoryDepthKey] }

      val childEnvironment = remember(environment) {
        environment + (FactoryDepthKey to depth + 1)
      }
      val modifier = transformer.modifyView(depth, environment)
      realFactory.showRendering(rendering, childEnvironment, modifier)
    }
  }

  /**
   * Values actually encode both depth and prevent infinite looping.
   * Even values mean the factory should do processing, odd values mean
   * direct pass-through.
   */
  private object FactoryDepthKey : ViewEnvironmentKey<Int>(Int::class) {
    override val default: Int get() = 0
  }
}
