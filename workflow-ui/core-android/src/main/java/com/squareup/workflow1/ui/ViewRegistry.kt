@file:Suppress("FunctionName")

package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ViewRegistry.Entry
import kotlin.reflect.KClass

/**
 * [ViewFactory]s that are always available.
 */
@WorkflowUiExperimentalApi
internal val defaultViewFactories = ViewRegistry(NamedViewFactory)

@WorkflowUiExperimentalApi
interface ViewRegistry {
  interface Entry<in RenderingT : Any> {
    val type: KClass<in RenderingT>
  }

  val keys: Set<KClass<*>>

  fun <RenderingT : Any> getEntryFor(
    renderingType: KClass<out RenderingT>
  ): Entry<RenderingT>
  companion object : ViewEnvironmentKey<ViewRegistry>(ViewRegistry::class) {
    override val default: ViewRegistry
      get() = error("There should always be a ViewRegistry hint, this is bug in Workflow.")
  }
}

@WorkflowUiExperimentalApi
fun ViewRegistry(vararg bindings: Entry<*>): ViewRegistry = TypedViewRegistry(*bindings)

/**
 * Returns a [ViewRegistry] that merges all the given [registries].
 */
@WorkflowUiExperimentalApi
fun ViewRegistry(vararg registries: ViewRegistry): ViewRegistry = CompositeViewRegistry(*registries)

/**
 * Returns a [ViewRegistry] that contains no bindings.
 *
 * Exists as a separate overload from the other two functions to disambiguate between them.
 */
@WorkflowUiExperimentalApi
fun ViewRegistry(): ViewRegistry = TypedViewRegistry()

@WorkflowUiExperimentalApi
operator fun ViewRegistry.plus(binding: Entry<*>): ViewRegistry =
  this + ViewRegistry(binding)

@WorkflowUiExperimentalApi
operator fun ViewRegistry.plus(other: ViewRegistry): ViewRegistry = ViewRegistry(this, other)
