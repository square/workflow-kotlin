package com.squareup.workflow1.ui

/**
 * Allows [ViewableRendering]s that do not implement [Compatible] themselves to be distinguished
 * by more than just their type. Instances are [compatible] if they have the same name
 * and have [compatible] [actual] fields.
 */
@WorkflowUiExperimentalApi
public data class NamedRendering<W : ViewableRendering>(
  override val actual: W,
  val name: String
) : AliasRendering {
  init {
    require(name.isNotBlank()) { "name must not be blank." }
  }

  override val compatibilityKey: String = Compatible.keyFor(actual, name)

  override fun toString(): String {
    return "${super.toString()}: $compatibilityKey"
  }
}

@WorkflowUiExperimentalApi
public fun <T: ViewableRendering> T.withName(name: String): NamedRendering<T> {
  return NamedRendering(this, name)
}
