package com.squareup.workflow1.ui

/**
 * Allows [Screen] renderings that do not implement [Compatible] themselves to be distinguished
 * by more than just their type. Instances are [compatible] if they have the same name
 * and have [compatible] [wrapped] fields.
 */
@WorkflowUiExperimentalApi
public data class NamedScreen<W : Screen>(
  override val actual: W,
  val name: String
) : AliasScreen, Compatible {
  init {
    require(name.isNotBlank()) { "name must not be blank." }
  }

  override val compatibilityKey: String = Compatible.keyFor(actual, name)

  override fun toString(): String {
    return "${super.toString()}: $compatibilityKey"
  }
}
