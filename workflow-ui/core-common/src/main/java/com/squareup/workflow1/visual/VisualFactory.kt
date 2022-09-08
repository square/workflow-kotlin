package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
public typealias AnyVisualFactory<ContextT, VisualT> = VisualFactory<ContextT, Any, VisualT>

@WorkflowUiExperimentalApi
public interface VisualFactory<ContextT, in RenderingT, out VisualT> {
  public fun createOrNull(
    rendering: RenderingT,
    context: ContextT,
    environment: VisualEnvironment
  ): VisualHolder<RenderingT, VisualT>?
}

@WorkflowUiExperimentalApi
public inline operator fun <C, reified R, reified S, V> VisualFactory<C, R, V>.plus(
  other: VisualFactory<C, S, V>
): VisualFactory<C, Any, V> {
  return SequentialVisualFactory<C, V>().let {
    it + this + other
  }
}

@WorkflowUiExperimentalApi
public interface VisualHolder<in RenderingT, out VisualT> {
  public val visual: VisualT

  public fun update(rendering: RenderingT): Boolean
}

/**
 * Base type for the factories that don't need a rendering to decide how to build the visual
 * (that is, they can implement a create method that returns an uninitialized Holder).
 */
@WorkflowUiExperimentalApi
public abstract class SimpleVisualFactory<ContextT, RenderingT, VisualT> :
  VisualFactory<ContextT, RenderingT, VisualT> {

  public abstract fun create(
    context: ContextT,
    environment: VisualEnvironment = VisualEnvironment.EMPTY
  ): VisualHolder<RenderingT, VisualT>

  public final override fun createOrNull(
    rendering: RenderingT,
    context: ContextT,
    environment: VisualEnvironment
  ): VisualHolder<RenderingT, VisualT> {
    return create(context, environment).also { it.update(rendering) }
  }
}

@WorkflowUiExperimentalApi
public interface VisualFactoryConverter<ContextT, VisualT, ContextU, VisualU> {
  public fun <RenderingT> convert(
    original: VisualFactory<ContextT, RenderingT, VisualT>
  ): VisualFactory<ContextU, RenderingT, VisualU>
}

@WorkflowUiExperimentalApi
public fun <C, R, V> VisualFactory<C, R, V>.mapEnvironment(
  transform: (VisualEnvironment) -> VisualEnvironment
): VisualFactory<C, R, V> {
  val delegate = this
  return object : VisualFactory<C, R, V> {
    override fun createOrNull(
      rendering: R,
      context: C,
      environment: VisualEnvironment
    ) = delegate.createOrNull(rendering, context, transform(environment))
  }
}
