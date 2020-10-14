package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
data class NamedViewRendering(
  override val wrapped: ViewRendering,
  override val name: String
) : NamedCompatible<ViewRendering>, ViewRendering {
  init {
    require(name.isNotBlank()) { "name must not be blank" }
  }

  override fun toString(): String {
    return "${super.toString()}: $compatibilityKey"
  }
}
