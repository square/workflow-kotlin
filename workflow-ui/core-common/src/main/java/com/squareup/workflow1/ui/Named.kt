package com.squareup.workflow1.ui

/**
 * **This will be deprecated in favor of [NamedScreen] very soon.**
 *
 * Allows renderings that do not implement [Compatible] themselves to be distinguished
 * by more than just their type. Instances are [compatible] if they have the same name
 * and have [compatible] [wrapped] fields.
 */
@WorkflowUiExperimentalApi
@Deprecated("Use NamedScreen")
public data class Named<W : Any>(
  val wrapped: W,
  val name: String
) : Compatible {
  init {
    require(name.isNotBlank()) { "name must not be blank." }
  }

  override val compatibilityKey: String = Compatible.keyFor(wrapped, "Named($name)")

  override fun toString(): String {
    return "${super.toString()}: $compatibilityKey"
  }
}
