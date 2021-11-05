package com.squareup.workflow1.ui.modal

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * May show a stack of [AlertScreen] over a [beneathModals].
 *
 * @param B the type of [beneathModals]
 */
@WorkflowUiExperimentalApi
public data class AlertContainerScreen<B : Any>(
  override val beneathModals: B,
  override val modals: List<AlertScreen> = emptyList()
) : Screen, HasModals<B, AlertScreen> {
  public constructor(
    baseScreen: B,
    alert: AlertScreen
  ) : this(baseScreen, listOf(alert))

  public constructor(
    baseScreen: B,
    vararg alerts: AlertScreen
  ) : this(baseScreen, alerts.toList())
}
