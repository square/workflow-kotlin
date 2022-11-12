package com.squareup.workflow1.visual

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactoryFinder
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.visual.ContextOrContainer.AndroidContainer
import com.squareup.workflow1.visual.ContextOrContainer.AndroidContext

// TODO: Should R extend Screen? Seems likely. And perhaps a high order
//  built in AndroidViewFactory for Screen could be responsible for keeping
//  View.environmentOrNull working.

@WorkflowUiExperimentalApi
public typealias AndroidViewFactory<R> = VisualFactory<ContextOrContainer, R, View>

@WorkflowUiExperimentalApi
public sealed interface ContextOrContainer {
  public class AndroidContext(public val context: Context) : ContextOrContainer

  public class AndroidContainer(public val container: ViewGroup) : ContextOrContainer
}

@WorkflowUiExperimentalApi
public val ContextOrContainer.context: Context
  get() = when (this) {
    is AndroidContext -> context
    is AndroidContainer -> container.context
  }

/**
 * Provides the composite [VisualFactory] that defines all of an app's
 * concrete Android [View] builders. Replaces [ScreenViewFactoryFinder].
 * Must not include any [VisualFactoryConverter] products,
 * see [visualizerAndroidViewFactory] for their use.
 *
 * The default instance supports [AndroidScreen], and backward compatibility
 * with the deprecated [ScreenViewFactoryFinder]. Apps can use the
 * [VisualFactory.plus] operator to build on that, or redefine it.
 *
 * [VisualizerAndroidViewFactoryKey] combines this factory with support for general
 * wrapper types like `NamedScreen`, and any [VisualFactoryConverter] duties --
 * e.g., combining this factory
 */
@WorkflowUiExperimentalApi
public val VisualEnvironment.leafAndroidViewFactory: AndroidViewFactory<Any>
  get() = this[LeafAndroidViewFactoryKey]

/**
 * Returns a copy of the receiving [VisualEnvironment] that uses the given
 * [factory] as its [leafAndroidViewFactory].
 */
@WorkflowUiExperimentalApi
public fun VisualEnvironment.withLeafAndroidViewFactory(
  factory: AndroidViewFactory<Any>
): VisualEnvironment {
  return this + (LeafAndroidViewFactoryKey to factory)
}

@WorkflowUiExperimentalApi
public inline fun <R, reified V : View> androidViewFactoryFromLayout(
  @IdRes resId: Int,
  crossinline constructor: (view: View, environment: VisualEnvironment) -> VisualHolder<R, V>
): VisualFactory<ContextOrContainer, R, V> = VisualFactory { _, context, environment, _ ->
  val view = LayoutInflater.from(context.context).inflate(resId, null, false) as V
  constructor(view, environment)
}

@WorkflowUiExperimentalApi
private object LeafAndroidViewFactoryKey : VisualEnvironmentKey<AndroidViewFactory<Any>>() {
  override val default: AndroidViewFactory<Any>
    get() = AndroidViewFactory { rendering, context, environment, _ ->
      // TODO find and convert the entire ScreenViewFactoryFinder instead
      //  in case it's been customized. Or does that happen with the multi key?
      //  Unclear to me if AndroidScreen / ScreenViewFactoryFinder legacy support
      //  belongs here or in AndroidViewMultiRenderingKey. But what definitely
      //  _does_ belong here are hooks for BackStackContainer, etc.
      //  Maybe we collect several standard factories here: one for legacy conversion,
      //  one for standard containers.

      (rendering as? AndroidScreen<*>)?.let { screen ->
        @Suppress("UNCHECKED_CAST")
        val oldHolder = (screen.viewFactory as ScreenViewFactory<Screen>)
          .buildView(screen, environment, context.context)

        VisualHolder(oldHolder.view) {
          oldHolder.runner.showRendering(it as Screen, environment)
        }
      }
        ?: (rendering as? Screen)?.let { screen ->
          val oldFactory = environment[ScreenViewFactoryFinder].getViewFactoryForRendering(
            environment, screen
          )

          // TODO either need to refactor ScreenViewHolder.startShowing out into something we
          //   can call directly, b/c it's too soon to call it here. Or else reproduce
          //   its very tricky support for both legacy startup machinery, and the Screen
          //   world's ViewStarter.
          oldFactory.buildView(screen, environment, context.context).let { oldHolder ->
            @Suppress("UNCHECKED_CAST")
            VisualHolder<Screen, View>(oldHolder.view) {
              oldHolder.show(it, environment)
            } as VisualHolder<Any, View>
          }
        }
    }
}
