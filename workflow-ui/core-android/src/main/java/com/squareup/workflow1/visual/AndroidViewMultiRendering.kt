package com.squareup.workflow1.visual

import android.content.Context
import android.view.View
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
public class AndroidViewMultiRendering : MultiRendering<Context, View>() {

  override fun create(
    rendering: Any,
    context: Context,
    environment: VisualEnvironment
  ): VisualHolder<Any, View> {
    return requireNotNull(
      environment[AndroidViewMultiRendering].createOrNull(rendering, context, environment)
    ) {
      "A VisualFactory must be registered to create an Android View for $rendering, " +
        "or it must implement AndroidScreen."
    }
  }

  /**
   * Provides the [AndroidViewFactory] that serves as the implementation of
   * [AndroidViewMultiRendering], allowing apps to customize the behavior
   * of `WorkflowViewStub` and other containers. E.g., workflow ui's optional
   * Compose integration will provide an alternative implementation that
   * extends this one to wrap Compose-specific VisualFactories in ComposeView.
   */
  public companion object : VisualEnvironmentKey<AndroidViewFactory>() {
    override val default: AndroidViewFactory
      get() = object : AndroidViewFactory {
        override fun createOrNull(
          rendering: Any,
          context: Context,
          environment: VisualEnvironment
        ): VisualHolder<Any, View>? {
          val concreteBindings = environment[AndroidViewFactoryKey]
          concreteBindings.createOrNull(rendering, context, environment)?.let {
            return it
          }

          return (rendering as? Named<*>)?.let {
            val namedFactory: VisualFactory<Context, Named<Any>, View> = named()
            namedFactory.createOrNull(
              it as Named<Any>, context, environment
            ) as? VisualHolder<Any, View>
          }
            ?: (rendering as? NamedScreen<*>)?.let {
              val namedFactory: VisualFactory<Context, NamedScreen<Screen>, View> = namedScreen()
              namedFactory.createOrNull(
                it as NamedScreen<Screen>, context, environment
              ) as? VisualHolder<Any, View>
            }
        }
      }
  }
}
