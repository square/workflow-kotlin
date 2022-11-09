package com.squareup.workflow1.ui.compose

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.visual.MultiRendering
import com.squareup.workflow1.visual.VisualEnvironment
import com.squareup.workflow1.visual.VisualHolder

@WorkflowUiExperimentalApi
public class ComposeMultiRendering : MultiRendering<Unit, ComposableLambda>() {
  override fun create(
    rendering: Any,
    context: Unit,
    environment: VisualEnvironment
  ): VisualHolder<Any, ComposableLambda> {
    return requireNotNull(
      environment[ComposableFactoryKey].createOrNull(rendering, Unit, environment)
    ) {
      // TODO error message
      "rendering: $rendering todo replace me with a message"
    }
  }
}
