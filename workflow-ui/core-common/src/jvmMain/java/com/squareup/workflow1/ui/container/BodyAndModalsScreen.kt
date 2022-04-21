package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * A screen that may stack a number of modal [Overlay]s over a body.
 * While modals are present, the body is expected to ignore any
 * input events -- touch, keyboard, etc.
 *
 * UI kits are expected to provide handling for this class by default.
 */
@WorkflowUiExperimentalApi
public class BodyAndModalsScreen<B : Screen, M : Overlay>(
  public val body: B,
  public val modals: List<M> = emptyList()
) : Screen {
  public constructor(
    body: B,
    vararg modals: M
  ) : this(body, modals.toList())

  public fun <S : Screen> mapBody(transform: (B) -> S): BodyAndModalsScreen<S, M> {
    return BodyAndModalsScreen(transform(body), modals)
  }

  public fun <O : Overlay> mapModals(transform: (M) -> O): BodyAndModalsScreen<B, O> {
    return BodyAndModalsScreen(body, modals.map(transform))
  }
}
