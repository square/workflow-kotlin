package com.squareup.workflow1.visual

import android.content.Context
import android.view.View
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * General support for classic Android container [View]s, used by `WorkflowViewStub`
 * et al. To be registered as a [VisualEnvironment] service keyed to [AndroidViewMultiRendering].
 *
 * Expected to be able to handle renderings of all type. That is, it:
 *
 * - Delegates to [AndroidViewFactoryKey] to handle renderings that are mapped directly
 *   to [View] based factories
 * - Supports general wrapper types like [Named], which are not [View] specific
 * - Handles conversions between types, e.g. wrapping any `Compose` based [VisualFactory]
 *   with `ComposeView`.
 *
 * The default implementation does not handle that last style of conversion, to avoid
 * making the general `core-android` module depend on the Compose runtime. The `compose`
 * module will bind an alternative implementation that adds these converters, for apps
 * to bind to the [AndroidViewMultiRendering] key.
 */
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

  public companion object : VisualEnvironmentKey<AndroidViewFactory<Any>>() {
    override val default: AndroidViewFactory<Any> = DefaultAndroidViewFactory
  }
}

/**
 * Provides the [AndroidViewFactory] that serves as the implementation of
 * [AndroidViewMultiRendering], allowing apps to customize the behavior
 * of `WorkflowViewStub` and other containers. E.g., workflow ui's optional
 * Compose integration will provide an alternative implementation that
 * extends this one to wrap Compose-specific VisualFactories in ComposeView.
 *
 * TODO: it is confusing that the VisualEnvironmentKey for this is
 *   AndroidViewMultiRendering. Perhaps make DefaultAndroidViewFactory public,
 *   and let it serve as its own key.
 *
 * TODO: This is seeming redundant with AndroidViewFactoryKey, is the split
 *   between them arbitrary? No, not redundant b/c this is the implementation
 *   of AndroidViewMultiRendering, which we will want to be able to replace with
 *   one that is aware of Compose. But probably the named stuff can't
 *   actually live here.
 *
 * TODO: Trying to go back to Named<>, but that won't work with the Screen
 *   and Overlay marker interfaces. How about something like this:
 *
 *    // Should be able to bind a VisualFactory to this interface, right?
 *    interface WithName<W> {
 *      val wrapped: W,
 *      val name: String
 *    } : Compatible {
 *
 *    }
 *
 *    internal class NamedScreen<W>(
 *      ...
 *    ) : WithName<W>, Screen
 *
 *    fun Screen.withName(name: String): Screen {
 *      return NamedScreen(this, name)
 *    }
 *
 *    // Same for interface WithEnvironment<W>
 *    // And same for Overlay
 */
@WorkflowUiExperimentalApi
private object DefaultAndroidViewFactory : AndroidViewFactory<Any> {
  override fun createOrNull(
    rendering: Any,
    context: Context,
    environment: VisualEnvironment
  ): VisualHolder<Any, View>? {
    environment[AndroidViewFactoryKey].createOrNull(rendering, context, environment)?.let {
      return it
    }

    return (rendering as? Named<*>)?.let {
      val namedFactory: VisualFactory<Context, Named<Any>, View> = named()
      @Suppress("UNCHECKED_CAST")
      namedFactory.createOrNull(
        it as Named<Any>, context, environment
      ) as? VisualHolder<Any, View>
    }
      ?: (rendering as? NamedScreen<*>)?.let {
        val namedFactory: VisualFactory<Context, NamedScreen<Screen>, View> = namedScreen()
        @Suppress("UNCHECKED_CAST")
        namedFactory.createOrNull(
          it as NamedScreen<Screen>, context, environment
        ) as? VisualHolder<Any, View>
      }
  }
}
