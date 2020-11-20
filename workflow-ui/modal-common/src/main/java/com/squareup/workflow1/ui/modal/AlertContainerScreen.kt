package com.squareup.workflow1.ui.modal

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * May show a stack of [AlertScreen] over a [beneathModals].
 *
 * @param B the type of [beneathModals]
 */
@WorkflowUiExperimentalApi
data class AlertContainerScreen<B : Any>(
  override val beneathModals: B,
  override val modals: List<AlertScreen> = emptyList()
) : HasModals<B, AlertScreen> {
  constructor(
    baseScreen: B,
    alert: AlertScreen
  ) : this(baseScreen, listOf(alert))

  constructor(
    baseScreen: B,
    vararg alerts: AlertScreen
  ) : this(baseScreen, alerts.toList())
}
