package com.squareup.workflow1.visual

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.IdRes
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
public typealias AndroidViewFactory = VisualFactory<Context, Any, View>

/**
 * Provides the composite [VisualFactory] that defines all of an app's
 * concrete Android [View] builders. Must not include any [VisualFactoryConverter]
 * products.
 *
 * The [default] implementation supports [AndroidScreen]. Apps can use the
 * [VisualFactory.plus] operator to build on that, or redefine it.
 *
 * See also [AndroidViewMultiRendering], which provides a higher level extension
 * point to combine this key with converters, e.g. to add Compose support.
 */
@WorkflowUiExperimentalApi
public object AndroidViewFactoryKey : VisualEnvironmentKey<AndroidViewFactory>() {
  override val default: AndroidViewFactory
    get() = object : AndroidViewFactory {
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
