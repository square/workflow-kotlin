package com.squareup.sample.container.panel

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Show a scrim over some [content], which is invisible if [dimmed] is false,
 * visible if it is true.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class ScrimScreen<T : Screen>(
  val content: T,
  val dimmed: Boolean
) : Screen, Compatible {
  override val compatibilityKey = keyFor(content, "ScrimScreen")
}
