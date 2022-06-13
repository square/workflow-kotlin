// @file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.modal

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * **This will be deprecated in favor of
 * [AlertOverlay][com.squareup.workflow1.ui.container.AlertOverlay] and
 * [BodyAndModalsScreen][com.squareup.workflow1.ui.container.BodyAndOverlaysScreen]
 * very soon.**
 *
 * May show a stack of [AlertScreen] over a [beneathModals].
 *
 * @param B the type of [beneathModals]
 */
@WorkflowUiExperimentalApi
// @Deprecated(
//   "Use BodyAndModalsScreen and AlertOverlay",
//   ReplaceWith(
//     "BodyAndModalsScreen<B>(beneathModals, modals)",
//     "com.squareup.workflow1.ui.container.BodyAndModalsScreen"
//   )
// )
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
