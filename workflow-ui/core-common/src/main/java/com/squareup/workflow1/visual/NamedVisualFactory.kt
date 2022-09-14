package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
public fun <C, R : Any, V> VisualFactory<C, R, V>.named(): VisualFactory<C, Named<R>, V> {
  val delegateFactory = this

  return object : VisualFactory<C, Named<R>, V> {
    override fun createOrNull(
      rendering: Named<R>,
      context: C,
      environment: VisualEnvironment
    ): VisualHolder<Named<R>, V>? {
      val delegateHolder = delegateFactory.createOrNull(rendering.wrapped, context, environment)
        ?: return null

      return object : VisualHolder<Named<R>, V> {
        override val visual: V get() = delegateHolder.visual

        override fun update(rendering: Named<R>): Boolean {
          return delegateHolder.update(rendering.wrapped)
        }
      }
    }
  }
}
