package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.Compatible.Companion

/**
 * Allows [Screen] renderings that do not implement [Compatible] themselves to be distinguished
 * by more than just their type. Instances are [compatible] if they have the same name
 * and have [compatible] [content] fields.
 *
 * UI kits are expected to provide handling for this class by default.
 */
@WorkflowUiExperimentalApi
public data class NamedScreen<out C : Screen>(
  override val content: C,
  val name: String
) : Screen, Wrapper<Screen, C> {
  init {
    require(name.isNotBlank()) { "name must not be blank." }
  }

  override val compatibilityKey: String = Companion.keyFor(content, "NamedScreen:$name")

  override fun <D : Screen> map(transform: (C) -> D): NamedScreen<D> =
    NamedScreen(transform(content), name)

  override fun toString(): String {
    return "${super.toString()}: $compatibilityKey"
  }
}
