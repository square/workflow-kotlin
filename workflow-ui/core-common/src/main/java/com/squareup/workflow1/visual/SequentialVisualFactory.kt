package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
public class SequentialVisualFactory<ContextT, VisualT>(
  public val selections: List<VisualFactory<ContextT, Any, VisualT>> = emptyList()
) : VisualFactory<ContextT, Any, VisualT> {
  public inline operator fun <reified T : Any> plus(
    factory: VisualFactory<ContextT, T, VisualT>
  ): SequentialVisualFactory<ContextT, VisualT> {
    // Give precedence to the new factory.
    val mut = selections.toMutableList().also { it.add(0, factory.widen()) }
    return SequentialVisualFactory(mut)
  }

  public override fun createOrNull(
    rendering: Any,
    context: ContextT,
    environment: VisualEnvironment
  ): VisualHolder<Any, VisualT>? = selections.firstNotNullOfOrNull {
    it.createOrNull(rendering, context, environment)
  }
}
