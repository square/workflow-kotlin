@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.modal

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * May show a stack of [AlertScreen] over a [beneathModals].
 *
 * @param B the type of [beneathModals]
 */
@WorkflowUiExperimentalApi
// Can't quite deprecate this yet, because such warnings are impossible to suppress
// in the typealias uses in the Tic Tac Toe sample. Will uncomment before merging to main.
// https://github.com/square/workflow-kotlin/issues/589
// @Deprecated(
//   "Use BodyAndModalsScreen",
//   ReplaceWith(
//     "BodyAndModalsScreen<B>(beneathModals, modals)",
//     "com.squareup.workflow1.ui.container.BodyAndModalsScreen"
//   )
// )
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
