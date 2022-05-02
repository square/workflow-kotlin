package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ViewRegistry.Entry
import kotlin.reflect.KClass

/**
 * A [ViewRegistry] that contains a set of [Entry]s, keyed by the [KClass]es of the
 * rendering types.
 */
@WorkflowUiExperimentalApi
internal class TypedViewRegistry private constructor(
  private val bindings: Map<KClass<*>, Entry<*>>
) : ViewRegistry {

  constructor(vararg bindings: Entry<*>) : this(
    bindings.associateBy { it.type }
      .apply {
        check(keys.size == bindings.size) {
          "${bindings.map { it.type }} must not have duplicate entries."
        }
      } as Map<KClass<*>, Entry<*>>
  )

  override val keys: Set<KClass<*>> get() = bindings.keys

  override fun <RenderingT : Any> getEntryFor(
    renderingType: KClass<out RenderingT>
  ): Entry<RenderingT>? {
    @Suppress("UNCHECKED_CAST")
    return bindings[renderingType] as? Entry<RenderingT>
  }

  override fun toString(): String {
    val map = bindings.map { "${it.key.simpleName}=${it.value::class.qualifiedName}" }
    return "TypedViewRegistry(bindings=$map)"
  }
}
