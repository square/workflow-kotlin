package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Decoration example to wrap the context. This will work for ANY VisualFactory whose input is
 * a ContextOrContainer.
 */
@WorkflowUiExperimentalApi
public fun <R, V> VisualFactory<ContextOrContainer, R, V>.decorateContext(
  decorationBlock: (ContextOrContainer) -> ContextOrContainer
): VisualFactory<ContextOrContainer, R, V> {
  val delegate = this
  return VisualFactory { rendering, context, environment, getFactory ->
    delegate.createOrNull(rendering, decorationBlock(context), environment, getFactory)
  }
}
