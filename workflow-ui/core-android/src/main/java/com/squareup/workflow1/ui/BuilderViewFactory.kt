package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

/**
 * A [ViewFactory] that allows a [ViewRegistry] to create [View]s that need
 * to be generated from code. (Use [LayoutRunner] to work with XML layout resources.)
 *
 * Typical usage is to have a custom builder or view's `companion object` implement
 * [ViewFactory] by delegating to a [BuilderViewFactory], like this:
 *
 *    class MyView(
 *      context: Context
 *    ) : FrameLayout(context, attributeSet) {
 *      private fun update(rendering:  MyRendering) { ... }
 *
 *      companion object : ViewBuilder<MyScreen>
 *      by BuilderBinding(
 *          type = MyScreen::class,
 *          viewConstructor = { initialRendering, _, context, _ ->
 *            MyView(context).apply {
 *              layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
 *              bindShowRendering(initialRendering, ::update)
 *            }
 *      )
 *    }
 *
 * This pattern allows us to assemble a [ViewRegistry] out of the
 * custom classes themselves.
 *
 *    val TicTacToeViewBuilders = ViewRegistry(
 *        MyView, GamePlayLayoutRunner, GameOverLayoutRunner
 *    )
 */
@WorkflowUiExperimentalApi
public class BuilderViewFactory<RenderingT : Any>(
  override val type: KClass<RenderingT>,
  private val viewConstructor: (
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ) -> View
) : ViewFactory<RenderingT> {
  override fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View = viewConstructor(initialRendering, initialViewEnvironment, contextForNewView, container)
}
