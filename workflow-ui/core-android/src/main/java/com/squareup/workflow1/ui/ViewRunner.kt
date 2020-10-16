package com.squareup.workflow1.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding

@WorkflowUiExperimentalApi
typealias ViewBindingInflater<BindingT> = (LayoutInflater, ViewGroup?, Boolean) -> BindingT

/**
 * A delegate that implements a [showRendering] method to be called when a workflow rendering
 * of type [RenderingT] is ready to be displayed in a view inflated from a layout resource
 * by a [ViewRegistry]. (Use [BuilderViewFactory] if you want to build views from code rather
 * than layouts.)
 *
 * If you're using [AndroidX ViewBinding][ViewBinding] you likely won't need to
 * implement this interface at all. For details, see the three overloads of [LayoutRunner.bind].
 */
@WorkflowUiExperimentalApi
interface ViewRunner<RenderingT : ViewRendering> {
  fun showRendering(
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  )

  companion object {
    /**
     * Creates a [ViewBuilder] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show renderings of type [RenderingT], using [a lambda][showRendering].
     *
     *    val HelloBinding: ViewBuilder<Rendering> =
     *      ViewRunner.bind(HelloGoodbyeLayoutBinding::inflate) { rendering, viewEnvironment ->
     *        helloMessage.text = rendering.message
     *        helloMessage.setOnClickListener { rendering.onClick(Unit) }
     *      }
     *
     * If you need to initialize your view before [showRendering] is called,
     * implement [ViewRunner] and create a binding using the `bind` variant
     * that accepts a `(ViewBinding) -> ViewRunner` function, below.
     */
    inline fun <BindingT : ViewBinding, reified RenderingT : ViewRendering> bind(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      crossinline showRendering: BindingT.(RenderingT, ViewEnvironment) -> Unit
    ): ViewBuilder<RenderingT> = bind(bindingInflater) { binding ->
      object : ViewRunner<RenderingT> {
        override fun showRendering(
          rendering: RenderingT,
          viewEnvironment: ViewEnvironment
        ) = binding.showRendering(rendering, viewEnvironment)
      }
    }

    /**
     * Creates a [ViewBuilder] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show
     * renderings of type [RenderingT], using a [ViewRunner] created by [constructor].
     * Handy if you need to perform some set up before [display] is called.
     *
     *   class HelloLayoutRunner(
     *     private val binding: HelloGoodbyeLayoutBinding
     *   ) : ViewRunner<Rendering> {
     *
     *     override fun showRendering(rendering: Rendering) {
     *       binding.messageView.text = rendering.message
     *       binding.messageView.setOnClickListener { rendering.onClick(Unit) }
     *     }
     *
     *     companion object : ViewBuilder<Rendering> by bind(
     *         HelloGoodbyeLayoutBinding::inflate, ::HelloLayoutRunner
     *     )
     *   }
     *
     * If the view doesn't need to be initialized before [display] is called,
     * use the variant above which just takes a lambda.
     */
    inline fun <BindingT : ViewBinding, reified RenderingT : ViewRendering> bind(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      noinline constructor: (BindingT) -> ViewRunner<RenderingT>
    ): ViewBuilder<RenderingT> =
      ViewBindingViewBuilder(RenderingT::class, bindingInflater, constructor)

    /**
     * Creates a [ViewBuilder] that inflates [layoutId] to show renderings of type [RenderingT],
     * using a [ViewRunner] created by [constructor]. Avoids any use of
     * [AndroidX ViewBinding][ViewBinding].
     */
    inline fun <reified RenderingT : ViewRendering> bind(
      @LayoutRes layoutId: Int,
      noinline constructor: (View) -> ViewRunner<RenderingT>
    ): ViewBuilder<RenderingT> = ViewRunnerViewBuilder(RenderingT::class, layoutId, constructor)

    /**
     * Creates a [ViewBuilder] that inflates [layoutId] to "show" renderings of type [RenderingT],
     * with a no-op [ViewRunner]. Handy for showing static views, e.g. when prototyping.
     */
    inline fun <reified RenderingT : ViewRendering> bindNoRunner(
      @LayoutRes layoutId: Int
    ): ViewBuilder<RenderingT> = bind(layoutId) {
      object : ViewRunner<RenderingT> {
        override fun showRendering(
          rendering: RenderingT,
          viewEnvironment: ViewEnvironment
        ) = Unit
      }
    }
  }
}

internal fun Context.viewBindingLayoutInflater(container: ViewGroup?) =
  LayoutInflater.from(container?.context ?: this)
      .cloneInContext(this)
