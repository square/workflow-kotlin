package com.squareup.sample.container.panel

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Show a scrim over the [wrapped] item, which is invisible if [dimmed] is false,
 * dark if it is true.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class ScrimContainerScreen<T : Screen>(
  val wrapped: T,
  val dimmed: Boolean
) : Screen
