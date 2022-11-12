package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Composite [VisualFactory] that delegates to its children in a specified order:
 *  - The members of [selections] are called in order
 *  - Elements added by [plus] are inserted at the head of [selections]
 */
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
    environment: VisualEnvironment,
    getFactory: (VisualEnvironment) -> VisualFactory<ContextT, Any, VisualT>
  ): VisualHolder<Any, VisualT>? = selections.firstNotNullOfOrNull {
    it.createOrNull(rendering, context, environment, getFactory)
  }
}
