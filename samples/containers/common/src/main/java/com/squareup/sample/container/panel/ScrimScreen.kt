package com.squareup.sample.container.panel

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenWrapper
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Show a scrim over some [content], which is invisible if [dimmed] is false,
 * visible if it is true.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class ScrimScreen<C : Screen>(
  override val content: C,
  val dimmed: Boolean
) : ScreenWrapper<C>, Screen {
  override fun <D : Screen> map(transform: (C) -> D) = ScrimScreen(transform(content), dimmed)
}
