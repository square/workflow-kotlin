package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

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

  public companion object {
    public operator fun <RenderingT, VisualT> invoke(
      visual: VisualT,
      onUpdate: (RenderingT) -> Unit
    ): VisualHolder<RenderingT, VisualT> {
      return object : VisualHolder<RenderingT, VisualT> {
        override val visual = visual

        override fun update(rendering: RenderingT): Boolean {
          onUpdate(rendering)
          return true
        }
      }
    }
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
