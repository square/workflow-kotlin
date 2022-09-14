package com.squareup.workflow1.ui

/**
 * Allows [Screen] renderings that do not implement [Compatible] themselves to be distinguished
 * by more than just their type. Instances are [compatible] if they have the same name
 * and have [compatible] [wrapped] fields.
 *
 * UI kits are expected to provide handling for this class by default.
 */
@WorkflowUiExperimentalApi
@Deprecated(
  "Rug pull! Use Named",
  ReplaceWith("Named(wrapped, name)")
)
public data class NamedScreen<W : Screen>(
  val wrapped: W,
  val name: String
) : Screen, Compatible {
  init {
    require(name.isNotBlank()) { "name must not be blank." }
  }

  override val compatibilityKey: String = Compatible.keyFor(wrapped, "NamedScreen($name)")

  override fun toString(): String {
    return "${super.toString()}: $compatibilityKey"
  }
}
