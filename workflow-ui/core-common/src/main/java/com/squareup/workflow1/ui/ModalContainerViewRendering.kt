package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
class ModalContainerViewRendering<out ViewT : ViewRendering, out ModalT : ModalRendering>(
  val beneathModals: ViewT,
  val modals: List<ModalT> = emptyList()
) : ViewRendering {
  constructor(
    beneathModals: ViewT,
    modal: ModalT
  ) : this(beneathModals, listOf(modal))
}
