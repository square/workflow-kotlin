package com.squareup.workflow1.ui

import kotlin.reflect.KClass

/**
 * A [ViewRegistry] that contains a set of [ViewFactory]s, keyed by the [KClass]es of the
 * rendering types.
 */
@WorkflowUiExperimentalApi
internal class TypedViewRegistry private constructor(
  private val bindings: Map<KClass<*>, ViewFactory<*>>
) : ViewRegistry {

  constructor(vararg bindings: ViewFactory<*>) : this(
      bindings.map { it.type to it }
          .toMap()
          .apply {
            check(keys.size == bindings.size) {
              "${bindings.map { it.type }} must not have duplicate entries."
            }
          } as Map<KClass<*>, ViewFactory<*>>
  )

  override val keys: Set<KClass<*>> get() = bindings.keys

  override fun <RenderingT : Any> getFactoryFor(
    renderingType: KClass<out RenderingT>
  ): ViewFactory<RenderingT> {
    @Suppress("UNCHECKED_CAST")
    return requireNotNull(bindings[renderingType] as? ViewFactory<RenderingT>) {
      "A ${ViewFactory::class.java.name} should have been registered " +
          "to display a $renderingType."
    }
  }
}
