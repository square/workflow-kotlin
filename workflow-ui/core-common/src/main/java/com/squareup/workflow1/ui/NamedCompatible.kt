package com.squareup.workflow1.ui

/**
 * Implemented by wrappers that allows renderings that do not implement [Compatible] themselves
 * to be distinguished by more than just their type. Instances are [compatible] if they
 * have the same name and have [compatible] [wrapped] fields.
 */
@WorkflowUiExperimentalApi
interface NamedCompatible<W : Any> : Compatible {
  val wrapped: W
  val name: String

  override val compatibilityKey: String get() = keyFor(wrapped, name)

  companion object {
    /**
     * Calculates the [NamedCompatible.compatibilityKey] for a given [value] and [name].
     */
    fun keyFor(
      value: Any,
      name: String = ""
    ): String {
      return ((value as? Compatible)?.compatibilityKey ?: value::class.java.name) + "-Named($name)"
    }
  }
}
