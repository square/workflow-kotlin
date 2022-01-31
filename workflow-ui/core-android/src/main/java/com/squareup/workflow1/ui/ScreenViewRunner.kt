package com.squareup.workflow1.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding

@WorkflowUiExperimentalApi
public typealias ViewBindingInflater<BindingT> = (LayoutInflater, ViewGroup?, Boolean) -> BindingT

/**
 * A delegate that implements a [showRendering] method to be called when a workflow
 * rendering of type [RenderingT] : [Screen] is ready to be displayed in a view created
 * by a [ScreenViewFactory].
 *
 * If you're using [AndroidX ViewBinding][ViewBinding] you likely won't need to
 * implement this interface at all. For details, see the three overloads of [ScreenViewRunner.bind].
 */
@WorkflowUiExperimentalApi
public fun interface ScreenViewRunner<RenderingT : Screen> {
  public fun showRendering(
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  )

  public companion object {
    /**
     * Creates a [ScreenViewFactory] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show renderings of type [RenderingT] : [Screen], using [a lambda][showRendering].
     *
     *    val HelloViewFactory: ScreenViewFactory<HelloScreen> =
     *      ScreenViewRunner.bind(HelloGoodbyeViewBinding::inflate) { rendering, viewEnvironment ->
     *        helloMessage.text = rendering.message
     *        helloMessage.setOnClickListener { rendering.onClick(Unit) }
     *      }
     *
     * If you need to initialize your view before [showRendering] is called,
     * implement [ScreenViewRunner] and create a binding using the `bind` variant
     * that accepts a `(ViewBinding) -> ScreenViewRunner` function, below.
     */
    public inline fun <BindingT : ViewBinding, reified RenderingT : Screen> bind(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      crossinline showRendering: BindingT.(RenderingT, ViewEnvironment) -> Unit
    ): ScreenViewFactory<RenderingT> = bind(bindingInflater) { binding ->
      ScreenViewRunner { rendering, viewEnvironment ->
        binding.showRendering(rendering, viewEnvironment)
      }
    }

    /**
     * Creates a [ScreenViewFactory] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show renderings of type [RenderingT] : [Screen], using a [ScreenViewRunner]
     * created by [constructor]. Handy if you need to perform some set up before
     * [showRendering] is called.
     *
     *   class HelloScreenRunner(
     *     private val binding: HelloGoodbyeViewBinding
     *   ) : ScreenViewRunner<HelloScreen> {
     *
     *     override fun showRendering(rendering: HelloScreen) {
     *       binding.messageView.text = rendering.message
     *       binding.messageView.setOnClickListener { rendering.onClick(Unit) }
     *     }
     *
     *     companion object : ScreenViewFactory<HelloScreen> by bind(
     *       HelloGoodbyeViewBinding::inflate, ::HelloScreenRunner
     *     )
     *   }
     *
     * If the view doesn't need to be initialized before [showRendering] is called,
     * use the variant above which just takes a lambda.
     */
    public inline fun <BindingT : ViewBinding, reified RenderingT : Screen> bind(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      noinline constructor: (BindingT) -> ScreenViewRunner<RenderingT>
    ): ScreenViewFactory<RenderingT> =
      ViewBindingScreenViewFactory(RenderingT::class, bindingInflater, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to show renderings of
     * type [RenderingT]  : [Screen], using a [ScreenViewRunner] created by [constructor].
     * Avoids any use of [AndroidX ViewBinding][ViewBinding].
     */
    public inline fun <reified RenderingT : Screen> bind(
      @LayoutRes layoutId: Int,
      noinline constructor: (View) -> ScreenViewRunner<RenderingT>
    ): ScreenViewFactory<RenderingT> =
      LayoutScreenViewFactory(RenderingT::class, layoutId, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to "show" renderings of type [RenderingT],
     * with a no-op [ScreenViewRunner]. Handy for showing static views, e.g. when prototyping.
     */
    @Suppress("unused")
    public inline fun <reified RenderingT : Screen> bindNoRunner(
      @LayoutRes layoutId: Int
    ): ScreenViewFactory<RenderingT> = bind(layoutId) { ScreenViewRunner { _, _ -> } }
  }
}

internal fun Context.viewBindingLayoutInflater(container: ViewGroup?) =
  LayoutInflater.from(container?.context ?: this)
    .cloneInContext(this)
