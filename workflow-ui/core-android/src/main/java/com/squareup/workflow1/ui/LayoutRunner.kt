package com.squareup.workflow1.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind

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
interface LayoutRunner<RenderingT : Any> {
  fun showRendering(
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  )

  companion object {
    /**
     * Creates a [ViewFactory] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show renderings of type [RenderingT], using [a lambda][showRendering].
     *
     *    val HelloBinding: ViewFactory<Rendering> =
     *      LayoutRunner.bind(HelloGoodbyeLayoutBinding::inflate) { rendering, viewEnvironment ->
     *        helloMessage.text = rendering.message
     *        helloMessage.setOnClickListener { rendering.onClick(Unit) }
     *      }
     *
     * If you need to initialize your view before [showRendering] is called,
     * implement [LayoutRunner] and create a binding using the `bind` variant
     * that accepts a `(ViewBinding) -> LayoutRunner` function, below.
     */
    inline fun <BindingT : ViewBinding, reified RenderingT : Any> bind(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      crossinline showRendering: BindingT.(RenderingT, ViewEnvironment) -> Unit
    ): ViewFactory<RenderingT> = bind(bindingInflater) { binding ->
      object : LayoutRunner<RenderingT> {
        override fun showRendering(
          rendering: RenderingT,
          viewEnvironment: ViewEnvironment
        ) = binding.showRendering(rendering, viewEnvironment)
      }
    }

    /**
     * Creates a [ViewFactory] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show
     * renderings of type [RenderingT], using a [LayoutRunner] created by [constructor].
     * Handy if you need to perform some set up before [showRendering] is called.
     *
     *   class HelloLayoutRunner(
     *     private val binding: HelloGoodbyeLayoutBinding
     *   ) : LayoutRunner<Rendering> {
     *
     *     override fun showRendering(rendering: Rendering) {
     *       binding.messageView.text = rendering.message
     *       binding.messageView.setOnClickListener { rendering.onClick(Unit) }
     *     }
     *
     *     companion object : ViewFactory<Rendering> by bind(
     *         HelloGoodbyeLayoutBinding::inflate, ::HelloLayoutRunner
     *     )
     *   }
     *
     * If the view doesn't need to be initialized before [showRendering] is called,
     * use the variant above which just takes a lambda.
     */
    inline fun <BindingT : ViewBinding, reified RenderingT : Any> bind(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      noinline constructor: (BindingT) -> LayoutRunner<RenderingT>
    ): ViewFactory<RenderingT> =
      ViewBindingViewFactory(RenderingT::class, bindingInflater, constructor)

    /**
     * Creates a [ViewFactory] that inflates [layoutId] to show renderings of type [RenderingT],
     * using a [LayoutRunner] created by [constructor]. Avoids any use of
     * [AndroidX ViewBinding][ViewBinding].
     */
    inline fun <reified RenderingT : Any> bind(
      @LayoutRes layoutId: Int,
      noinline constructor: (View) -> LayoutRunner<RenderingT>
    ): ViewFactory<RenderingT> = LayoutRunnerViewFactory(RenderingT::class, layoutId, constructor)

    /**
     * Creates a [ViewFactory] that inflates [layoutId] to "show" renderings of type [RenderingT],
     * with a no-op [LayoutRunner]. Handy for showing static views, e.g. when prototyping. e.g. when prototyping.
     */
    inline fun <reified RenderingT : Any> bindNoRunner(
      @LayoutRes layoutId: Int
    ): ViewFactory<RenderingT> = bind(layoutId) {
      object : LayoutRunner<RenderingT> {
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
