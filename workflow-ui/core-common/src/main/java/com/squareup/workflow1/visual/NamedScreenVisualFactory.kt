@file:Suppress("DEPRECATION")

package com.squareup.workflow1.visual

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
    ): VisualHolder<NamedScreen<R>, V> {
      val delegateHolder = requireNotNull(
        delegateFactory.createOrNull(rendering.wrapped, context, environment)
      ) {
        // TODO copy / pasting this error message all over the place is a drag, but
        //   how else do we get an error message of the actual nested rendering that
        //   is unbound? Maybe throw a custom exception type that AndroidViewMultiRendering
        //   can watch for and repackage. Maybe a base function to create these wrappers
        //   that has the message built in.
        "A VisualFactory must be registered to create an Android View for " +
          "${rendering.wrapped}, or it must implement AndroidScreen."
      }

      return object : VisualHolder<NamedScreen<R>, V> {
        override val visual: V get() = delegateHolder.visual

        override fun update(rendering: NamedScreen<R>): Boolean {
          return delegateHolder.update(rendering.wrapped)
        }
      }
    }
  }
}
