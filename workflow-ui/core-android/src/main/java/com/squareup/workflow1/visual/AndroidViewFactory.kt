package com.squareup.workflow1.visual

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.IdRes
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactoryFinder
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.visual.AndroidViewFactoryKey.default

@WorkflowUiExperimentalApi
public typealias AndroidViewFactory<R> = VisualFactory<Context, R, View>

/**
 * Provides the composite [VisualFactory] that defines all of an app's
 * concrete Android [View] builders. Must not include any [VisualFactoryConverter]
 * products, see [AndroidViewMultiRendering] for their use.
 *
 * The [default] implementation supports [AndroidScreen], and backward compatibility
 * with the deprecated [ScreenViewFactoryFinder]. Apps can use the
 * [VisualFactory.plus] operator to build on that, or redefine it.
 *
 * [AndroidViewMultiRendering] combines this factory with support for general
 * wrapper types like `Named`, and any [VisualFactoryConverter] duties.
 */
@WorkflowUiExperimentalApi
public object AndroidViewFactoryKey : VisualEnvironmentKey<AndroidViewFactory<Any>>() {
  override val default: AndroidViewFactory<Any>
    get() = object : AndroidViewFactory<Any> {
      override fun createOrNull(
        rendering: Any,
        context: Context,
        environment: VisualEnvironment
      ): VisualHolder<Any, View>? {

        // TODO find and convert the entire ScreenViewFactoryFinder instead.
        //   or does that happen with the multi key?

        return (rendering as? AndroidScreen<*>)?.let { screen ->
            @Suppress("UNCHECKED_CAST")
            val oldHolder = (screen.viewFactory as ScreenViewFactory<Screen>)
              .buildView(screen, environment, context)

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
            // TODO AndroidViewFactory ContextT needs to be Pair<Context, ViewGroup?>, latter
            //   is the optional container view required by, uhm, theming or something.
            oldFactory.buildView(screen, environment, context).let { oldHolder ->
              @Suppress("UNCHECKED_CAST")
              VisualHolder<Screen, View>(oldHolder.view) {
                oldHolder.show(it, environment)
              } as VisualHolder<Any, View>
            }
          }
      }
    }
}

/**
 * Convenience to access any Android view Holder's output as `androidView`.
 */
@WorkflowUiExperimentalApi
public val <ViewT : View> VisualHolder<*, ViewT>.androidView: ViewT
  get() = visual

// TODO: R should extend Screen I think. So Screen is enforced for leaf factories,
//   but things like AndroidViewMultiRendering stay based on Any so that they
//   can handle generic concerns like WithName() and WithEnvironment()
@WorkflowUiExperimentalApi
public fun <R, V : View> androidViewFactoryFromCode(
  build: (context: Context, environment: VisualEnvironment) -> VisualHolder<R, V>
): VisualFactory<Context, R, V> = object : VisualFactory<Context, R, V> {
  override fun createOrNull(
    rendering: R,
    context: Context,
    environment: VisualEnvironment
  ): VisualHolder<R, V> {
    return build(context, environment)
  }
}

@WorkflowUiExperimentalApi
public fun <R, V : View> androidViewFactoryFromCode(
  build: (rendering: R, context: Context, environment: VisualEnvironment) -> VisualHolder<R, V>
): VisualFactory<Context, R, V> = object : VisualFactory<Context, R, V> {
  override fun createOrNull(
    rendering: R,
    context: Context,
    environment: VisualEnvironment
  ): VisualHolder<R, V> {
    return build(rendering, context, environment)
  }
}

@WorkflowUiExperimentalApi
public inline fun <R, reified V : View> androidViewFactoryFromLayout(
  @IdRes resId: Int,
  crossinline constructor: (view: View, environment: VisualEnvironment) -> VisualHolder<R, V>
): VisualFactory<Context, R, V> = androidViewFactoryFromCode { c, e ->
  val view = LayoutInflater.from(c).inflate(resId, null, false) as V
  constructor(view, e)
}
