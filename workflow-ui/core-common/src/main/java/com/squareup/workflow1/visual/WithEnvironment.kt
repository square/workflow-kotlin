package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
public abstract class WithEnvironment<W : Any>(
  wrapped: W,
  public val environment: VisualEnvironment
) : Wrapper<W>(wrapped)

@WorkflowUiExperimentalApi
public class WithEnvironmentVisualFactory<C, W : Any, V> : VisualFactory<C, WithEnvironment<W>, V> {
  override fun createOrNull(
    rendering: WithEnvironment<W>,
    context: C,
    environment: VisualEnvironment,
    getFactory: (VisualEnvironment) -> VisualFactory<C, Any, V>
  ): VisualHolder<WithEnvironment<W>, V> {
    val mergedEnvironment = environment + rendering.environment

    val delegateHolder = getFactory(mergedEnvironment).create(
      rendering.wrapped, context, mergedEnvironment, getFactory
    )

    return object : VisualHolder<WithEnvironment<W>, V> {
      override val visual: V get() = delegateHolder.visual

      override fun update(rendering: WithEnvironment<W>): Boolean {
        return delegateHolder.update(rendering.wrapped)
      }
    }
  }
}
