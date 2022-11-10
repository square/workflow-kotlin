package com.squareup.workflow1.visual

import android.view.View
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * General support for classic Android container [View]s, used by `WorkflowViewStub`
 * et al. To be registered as a [VisualEnvironment] service keyed to [AndroidViewMultiRendering].
 *
 * Expected to be able to handle renderings of all type. That is, it:
 *
 * - Delegates to [AndroidViewFactoryKey] to handle renderings that are mapped directly
 *   to [View] based factories
 * - Provides general support for the standard wrapper types based on [Wrapper], like
 *   `NamedScreen`. TODO: This can't be the right place for that, move it to super
 * - Handles conversions between types, e.g. wrapping any `Compose` based [VisualFactory]
 *   with `ComposeView`.
 *
 * The default implementation does not handle that last style of conversion, to avoid
 * making the general `core-android` module depend on the Compose runtime. The `compose`
 * module will bind an alternative implementation that adds these converters, for apps
 * to bind to the [AndroidViewMultiRendering] key.
 */
@OptIn(WorkflowUiExperimentalApi::class)
public class AndroidViewMultiRendering : MultiRendering<ContextOrContainer, View>() {

  override fun create(
    rendering: Any,
    context: ContextOrContainer,
    environment: VisualEnvironment
  ): VisualHolder<Any, View> {
    return environment[AndroidViewMultiRendering].create(
        rendering,
        context,
        environment
      )
  }

  /**
   * Provides the [AndroidViewFactory] that serves as the implementation of
   * [AndroidViewMultiRendering], allowing apps to customize the behavior
   * of `WorkflowViewStub` and other containers. E.g., workflow ui's optional
   * Compose integration will provide an alternative implementation that
   * extends this one to wrap Compose-specific VisualFactories in ComposeView.
   */
  public companion object : VisualEnvironmentKey<AndroidViewFactory<Any>>() {
    override val default: AndroidViewFactory<Any> = object : AndroidViewFactory<Any> {
      override fun createOrNull(
        rendering: Any,
        context: ContextOrContainer,
        environment: VisualEnvironment
      ): VisualHolder<Any, View>? {
        // TODO is this the right ordering? I just stuck `WithName` on top to ensure
        //  I could test it. And again, this belongs somewhere above any mention of Android

        (rendering as? WithName<*>)?.let {
          val namedFactory: VisualFactory<ContextOrContainer, WithName<Any>, View> = withName()
          @Suppress("UNCHECKED_CAST")
          return namedFactory.createOrNull(
            it as WithName<Any>, context, environment
          ) as? VisualHolder<Any, View>
        }

        return environment[AndroidViewFactoryKey].createOrNull(rendering, context, environment)
      }
    }
  }
}
