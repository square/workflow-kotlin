package com.squareup.sample.container.panel

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.Wrapper

/**
 * Show a scrim over some [content], which is invisible if [dimmed] is false,
 * visible if it is true.
 */
class ScrimScreen<C : Screen>(
  override val content: C,
  val dimmed: Boolean
) : Wrapper<Screen, C>, Screen {
  override fun <D : Screen> map(transform: (C) -> D) = ScrimScreen(transform(content), dimmed)
}
