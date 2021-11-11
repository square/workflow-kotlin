package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * A screen that may stack a number of modal [Overlay]s over a body.
 * While modals are present, the body is expected to ignore any
 * input events -- touch, keyboard, etc.
 */
@WorkflowUiExperimentalApi
public class BodyAndModalsScreen<B : Screen, M : Overlay>(
  public val body: B,
  public val modals: List<M> = emptyList()
) : Screen {
  public constructor(
    body: B,
    modal: M
  ) : this(body, listOf(modal))
}
