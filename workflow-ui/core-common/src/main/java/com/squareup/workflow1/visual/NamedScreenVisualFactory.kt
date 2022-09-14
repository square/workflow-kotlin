package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
public fun <C, R : Screen, V> VisualFactory<C, R, V>.namedScreen(): VisualFactory<C, NamedScreen<R>, V> {
  val delegateFactory = this

  return object : VisualFactory<C, NamedScreen<R>, V> {
    override fun createOrNull(
      rendering: NamedScreen<R>,
      context: C,
      environment: VisualEnvironment
    ): VisualHolder<NamedScreen<R>, V>? {
      val delegateHolder = delegateFactory.createOrNull(rendering.wrapped, context, environment)
        ?: return null

      return object : VisualHolder<NamedScreen<R>, V> {
        override val visual: V get() = delegateHolder.visual

        override fun update(rendering: NamedScreen<R>): Boolean {
          return delegateHolder.update(rendering.wrapped)
        }
      }
    }
  }
}
