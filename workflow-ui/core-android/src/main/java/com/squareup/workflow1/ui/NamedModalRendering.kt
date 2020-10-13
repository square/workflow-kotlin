package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
data class NamedModalRendering<M : ModalRendering>(
  override val wrapped: M,
  override val name: String
) : NamedCompatible<M>, ModalRendering {
  init {
    require(name.isNotBlank()) { "name must not be blank." }
  }

  override fun toString(): String {
    return "${super.toString()}: $compatibilityKey"
  }
}
