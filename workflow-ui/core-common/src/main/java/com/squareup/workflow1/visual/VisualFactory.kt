package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

// TODO: Set up a default factory, one of the first in the chain, for
//  VisualFactory itself -- that is, allow renderings to implement
//  VisualFactory directly, and make sure such default implementations
//  can be overridden at runtime. Generalize the behavior of AndroidScreen,
//  AndroidOverlay, etc.
//
// TODO: Replace RenderingT and rendering with ModelT and model throughout.
//   Yes, Rendering is the standard workflow term, but I'm really starting
//   to believe that this library will outlive workflow. Let's begin as
//   we hope to proceed.
@WorkflowUiExperimentalApi
public interface VisualFactory<ContextT, in RenderingT, out VisualT> {
  /**
   * Given a ui model ([rendering]), creates a [VisualHolder] which pairs:
   *
   * - a native view system object of type [VisualT] -- a [visual][VisualHolder.visual]
   * - an [update function][VisualHolder.update] to apply [RenderingT] instances to
   *   the new [VisualT] instance.
   *
   * This method must not call [VisualHolder.update], to ensure that callers have
   * complete control over the lifecycle of the new [VisualT].
   */
  public fun createOrNull(
    rendering: RenderingT,
    context: ContextT,
    environment: VisualEnvironment
  ): VisualHolder<RenderingT, VisualT>?
}

@WorkflowUiExperimentalApi
public fun <C, R, V> VisualFactory<C, R, V>.create(
  rendering: R,
  context: C,
  environment: VisualEnvironment
): VisualHolder<R, V> {
  return requireNotNull(createOrNull(rendering, context, environment)) {
    "A VisualFactory must be registered for ${rendering}, " +
      "or it must implement VisualFactory directly."
  }
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
