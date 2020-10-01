package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

/**
 * A [ViewBuilder] that allows a [ViewRegistry] to create [View]s that need
 * to be generated from code. (Use [ViewRunner] to work with XML layout resources.)
 *
 * Typical usage is to have a custom builder or view's `companion object` implement
 * [ViewBuilder] by delegating to a [BespokeViewBuilder], like this:
 *
 *    class MyView(
 *      context: Context
 *    ) : FrameLayout(context, attributeSet) {
 *      private fun update(rendering:  MyRendering) { ... }
 *
 *      companion object : ViewBuilder<MyRendering>
 *      by BuilderBinding(
 *          type = MyRendering::class,
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
 *        MyView, GamePlayViewRunner, GameOverViewRunner
 *    )
 */
@WorkflowUiExperimentalApi
class BespokeViewBuilder<RenderingT : ViewRendering>(
  override val type: KClass<RenderingT>,
  private val constructor: (
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ) -> View
) : ViewBuilder<RenderingT> {
  override fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View = constructor(initialRendering, initialViewEnvironment, contextForNewView, container)
}
