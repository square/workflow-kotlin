package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Base type for [Wrapper]s that are used to change the
 * [compatibility][com.squareup.workflow1.ui.compatible] of renderings
 * that do not implement [Compatible][com.squareup.workflow1.ui.Compatible]
 * themselves.
 */
@WorkflowUiExperimentalApi
public abstract class WithName<W : Any>(
  wrapped: W,
  name: String
) : Wrapper<W>(wrapped) {
  public final override val name: String by lazy {
    require(name.isNotBlank()) { "name must not be blank." }
    name
  }
}

@WorkflowUiExperimentalApi
public class WithNameVisualFactory<C, W : Any, V> : VisualFactory<C, WithName<W>, V> {
  override fun createOrNull(
    rendering: WithName<W>,
    context: C,
    environment: VisualEnvironment,
    getFactory: (VisualEnvironment) -> VisualFactory<C, Any, V>
  ): VisualHolder<WithName<W>, V> {
    val delegateHolder = getFactory(environment)
      .create(rendering.wrapped, context, environment, getFactory)

    return object : VisualHolder<WithName<W>, V> {
      override val visual: V get() = delegateHolder.visual

      override fun update(rendering: WithName<W>): Boolean {
        return delegateHolder.update(rendering.wrapped)
      }
    }
  }
}
