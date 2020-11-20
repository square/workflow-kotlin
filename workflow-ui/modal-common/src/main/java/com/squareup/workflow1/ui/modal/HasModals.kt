package com.squareup.workflow1.ui.modal

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Interface implemented by screen classes that represent a stack of
 * zero or more [modal][M] screens above a [base screen][beneathModals].
 *
 * Use of this interface allows platform specific containers to share base classes,
 * like `ModalContainer` in the `workflow-ui:core-android` module.
 */
@WorkflowUiExperimentalApi
interface HasModals<out B : Any, out M : Any> {
  val beneathModals: B
  val modals: List<M>
}
