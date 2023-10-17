package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ViewRegistry.Entry
import com.squareup.workflow1.ui.ViewRegistry.Key
import kotlin.reflect.KClass

/**
 * A [ViewRegistry] that contains a set of [Entry]s, keyed by the [KClass]es of the
 * rendering types.
 */
@WorkflowUiExperimentalApi
internal class TypedViewRegistry private constructor(
  private val bindings: Map<Key<*, *>, Entry<*>>
) : ViewRegistry {

  constructor(vararg bindings: Entry<*>) : this(
    bindings.associateBy {
      require(it.key.factoryType.isInstance(it)) {
        "Factory $it must be of the type declared in its key, ${it.key.factoryType.qualifiedName}"
      }
      it.key
    }
      .apply {
        check(keys.size == bindings.size) {
          "${bindings.map { it.key }} must not have duplicate entries."
        }
      } as Map<Key<*, *>, Entry<*>>
  )

  override val keys: Set<Key<*, *>> get() = bindings.keys

  override fun <RenderingT : Any, FactoryT : Any> getEntryFor(
    key: Key<RenderingT, FactoryT>
  ): Entry<RenderingT>? {
    @Suppress("UNCHECKED_CAST")
    return bindings[key] as? Entry<RenderingT>
  }

  override fun toString(): String {
    val map = bindings.map { "${it.key}=${it.value::class.qualifiedName}" }
    return "TypedViewRegistry(bindings=$map)"
  }
}
