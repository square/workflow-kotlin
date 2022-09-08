package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
public class ExactTypeVisualFactory<ContextT, VisualT>(
  public val factories: Map<KClass<*>, VisualFactory<ContextT, Any, VisualT>>
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
    rendering,
    context,
    environment
  )
}

@WorkflowUiExperimentalApi
public fun <ContextT, RenderingT, VisualT> VisualFactory<ContextT, RenderingT, VisualT>.forItemsWhere(
  predicate: (RenderingT) -> Boolean
): VisualFactory<ContextT, RenderingT, VisualT> {
  val delegate = this
  return object : VisualFactory<ContextT, RenderingT, VisualT> {
    override fun createOrNull(
      rendering: RenderingT,
      context: ContextT,
      environment: VisualEnvironment
    ): VisualHolder<RenderingT, VisualT>? {
      return if (predicate(rendering)) {
        delegate.createOrNull(
          rendering,
          context,
          environment
        )?.let { delegateHolder ->
          object : VisualHolder<RenderingT, VisualT> by delegateHolder {
            override fun update(rendering: RenderingT): Boolean {
              return predicate(rendering) && delegateHolder.update(rendering)
            }
          }
        }
      } else null
    }
  }
}
