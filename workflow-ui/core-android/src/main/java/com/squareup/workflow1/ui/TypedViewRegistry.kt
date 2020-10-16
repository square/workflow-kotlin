package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ViewRegistry.Entry
import kotlin.reflect.KClass

/**
 * A [ViewRegistry] that contains a set of [ViewFactory]s, keyed by the [KClass]es of the
 * rendering types.
 */
@Suppress("DEPRECATION", "OverridingDeprecatedMember")
@WorkflowUiExperimentalApi
internal class TypedViewRegistry private constructor(
  private val bindings: Map<KClass<*>, Entry<*>>
) : ViewRegistry {

  constructor(vararg bindings: Entry<*>) : this(
      bindings.map { it.type to it }
          .toMap()
          .apply {
            check(keys.size == bindings.size) {
              "${bindings.map { it.type }} must not have duplicate entries."
            }
          } as Map<KClass<*>, Entry<*>>
  )

  override val keys: Set<KClass<*>> get() = bindings.keys

  override fun <RenderingT : Any> getEntryFor(
    renderingType: KClass<out RenderingT>
  ): Entry<RenderingT> {
    @Suppress("UNCHECKED_CAST")
    return requireNotNull(bindings[renderingType] as? Entry<RenderingT>) {
      "An entry should have been registered to display $renderingType."
    }
  }

  override fun <RenderingT : Any> getFactoryFor(
    renderingType: KClass<out RenderingT>
  ): ViewFactory<RenderingT> = getEntryFor(renderingType) as ViewFactory<RenderingT>
}
