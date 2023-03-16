package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * A screen that may stack a number of [Overlay]s over a body.
 * If any members of [overlays] are [ModalOverlay], the body and
 * lower-indexed members of that list are expected to ignore input
 * events -- touch, keyboard, etc.
 *
 * UI kits are expected to provide handling for this class by default.
 *
 * @param name included in the [compatibilityKey] of this screen, for ease
 * of nesting -- on Android, each BodyAndOverlaysScreen view state persistence
 * support requires each BodyAndOverlaysScreen in a hierarchy to have a
 * unique key
 */
@WorkflowUiExperimentalApi
public class BodyAndOverlaysScreen<B : Screen, O : Overlay>(
  public val body: B,
  public val overlays: List<O> = emptyList(),
  public val name: String = ""
) : Screen, Compatible {
  override val compatibilityKey: String = keyFor(this, name)

  public constructor(
    body: B,
    vararg modals: O
  ) : this(body, modals.toList())

  public fun <S : Screen> mapBody(transform: (B) -> S): BodyAndOverlaysScreen<S, O> {
    return BodyAndOverlaysScreen(transform(body), overlays)
  }

  public fun <N : Overlay> mapModals(transform: (O) -> N): BodyAndOverlaysScreen<B, N> {
    return BodyAndOverlaysScreen(body, overlays.map(transform))
  }
}
