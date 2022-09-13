package com.squareup.workflow1.visual

import android.content.Context
import android.view.View
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
public class AndroidViewMultiRendering : MultiRendering<Context, View>() {

  override fun create(
    rendering: Any,
    context: Context,
    environment: VisualEnvironment
  ): VisualHolder<Any, View> {
    return requireNotNull(
      environment[AndroidViewFactoryKey].createOrNull(rendering, context, environment)
    ) {
      "A VisualFactory must be registered to create an Android View for $rendering, " +
        "or it must implement AndroidScreen."
    }
  }
}
