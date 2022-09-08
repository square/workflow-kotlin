package com.squareup.workflow1.visual

import android.content.Context
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Let's say the usual Android view factory does not set the lifecycle owner to the view
 * (we just don't like setting tags on views unnecessarily). We can decorate a view factory
 * to do it without ANY knowledge of anything else.
 *
 * Notice that this extension method is on ANY factory that produces views even if it's not our
 * standard AndroidViewFactory. That means it would work with any other input even if it's not our
 * usual Context.
 *
 * TODO: probably don't actually want this to be public, we need to monopolize use of the
 *   LifecycleOwner, but it's a good reminder -- rjrjr
 */
@WorkflowUiExperimentalApi
public fun <C, R, V : View> VisualFactory<C, R, V>.setLifecycleOwner(
  lifecycleOwner: LifecycleOwner
): VisualFactory<C, R, V> {
  val delegate = this
  return object : VisualFactory<C, R, V> {
    override fun createOrNull(
      rendering: R,
      context: C,
      environment: VisualEnvironment
    ): VisualHolder<R, V>? =
      delegate.createOrNull(
        rendering,
        context,
        environment
      )?.also { ViewTreeLifecycleOwner.set(it.androidView, lifecycleOwner) }
  }
}

/**
 * Decoration example to wrap the context. This will work for ANY VisualFactory whose input is
 * a Context.
 */
@WorkflowUiExperimentalApi
public fun <RenderingT, VisualT> VisualFactory<Context, RenderingT, VisualT>.decorateContext(
  decorationBlock: (Context) -> Context
): VisualFactory<Context, RenderingT, VisualT> {
  val delegate = this
  return object : VisualFactory<Context, RenderingT, VisualT> {
    override fun createOrNull(
      rendering: RenderingT,
      context: Context,
      environment: VisualEnvironment
    ) = delegate.createOrNull(rendering, decorationBlock(context), environment)
  }
}
