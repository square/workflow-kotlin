package com.squareup.workflow1.ui.modal

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * **This will be deprecated in favor of
 * [AlertOverlay][com.squareup.workflow1.ui.container.AlertOverlay] and
 * [BodyAndOverlaysScreen][com.squareup.workflow1.ui.container.BodyAndOverlaysScreen]
 * very soon.**
 *
 * May show a stack of [AlertScreen] over a [beneathModals].
 *
 * @param B the type of [beneathModals]
 */
@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
@Deprecated(
  "Use BodyAndOverlaysScreen and AlertOverlay",
  ReplaceWith(
    "BodyAndOverlaysScreen<B>(beneathModals, modals)",
    "com.squareup.workflow1.ui.container.BodyAndOverlaysScreen"
  )
)
public data class AlertContainerScreen<B : Any>(
  override val beneathModals: B,
  override val modals: List<AlertScreen> = emptyList()
) : HasModals<B, AlertScreen> {
  public constructor(
    baseScreen: B,
    alert: AlertScreen
  ) : this(baseScreen, listOf(alert))

  public constructor(
    baseScreen: B,
    vararg alerts: AlertScreen
  ) : this(baseScreen, alerts.toList())
}
