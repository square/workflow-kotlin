package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
public class ExactTypeVisualFactory<ContextT, VisualT>(
  public val factories: Map<KClass<*>, VisualFactory<ContextT, Any, VisualT>> = emptyMap()
) : VisualFactory<ContextT, Any, VisualT> {

  public inline operator fun <reified T : Any> plus(
    factory: VisualFactory<ContextT, T, VisualT>
  ): ExactTypeVisualFactory<ContextT, VisualT> {
    return ExactTypeVisualFactory(factories.toMutableMap().let {
      it[T::class] = factory.widen()
      it.toMap()
    })
  }

  public override fun createOrNull(
    rendering: Any,
    context: ContextT,
    environment: VisualEnvironment
  ): VisualHolder<Any, VisualT>? = factories[rendering::class]?.createOrNull(
    rendering, context, environment
  )
}
