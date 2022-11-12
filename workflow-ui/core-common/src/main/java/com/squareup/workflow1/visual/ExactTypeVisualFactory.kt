package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

/**
 * A composite [VisualFactory] that maps concrete rendering types to the specific
 * [VisualFactory] instances that can render them. That is, [createOrNull]
 * uses the `::class` of a given `rendering` to look up a matching factory, so
 * polymorphism is not supported.
 */
@WorkflowUiExperimentalApi
public class ExactTypeVisualFactory<ContextT, VisualT>

// TODO Lock down to prevent type mismatches. Make this class private, and
//  make a [VisualFactory]`.Companion` faux constructor that invokes this one,
//  like the one on [VisualHolder].
public constructor(
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
    environment: VisualEnvironment,
    getFactory: (VisualEnvironment) -> VisualFactory<ContextT, Any, VisualT>
  ): VisualHolder<Any, VisualT>? = factories[rendering::class]?.createOrNull(
    rendering, context, environment, getFactory
  )
}
