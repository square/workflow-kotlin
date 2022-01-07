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
 * Function that updates the UI built by a  [ScreenViewFactory].
 *
 * If you're using [AndroidX ViewBinding][ViewBinding] you likely won't need to
 * implement this interface at all. For details, see the three overloads of [ScreenViewUpdater.bind].
 */
@WorkflowUiExperimentalApi
public fun interface ScreenViewUpdater<RenderingT : Screen> {
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
     *      ScreenViewUpdater.bind(HelloGoodbyeViewBinding::inflate) { rendering, viewEnvironment ->
     *        helloMessage.text = rendering.message
     *        helloMessage.setOnClickListener { rendering.onClick(Unit) }
     *      }
     *
     * If you need to initialize your view before [showRendering] is called,
     * implement [ScreenViewUpdater] and create a binding using the `bind` variant
     * that accepts a `(ViewBinding) -> ScreenViewUpdater` function, below.
     */
    public inline fun <BindingT : ViewBinding, reified RenderingT : Screen> bind(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      crossinline showRendering: BindingT.(RenderingT, ViewEnvironment) -> Unit
    ): ScreenViewFactory<RenderingT> = bind(bindingInflater) { binding ->
      ScreenViewUpdater { rendering, viewEnvironment ->
        binding.showRendering(rendering, viewEnvironment)
      }
    }

    /**
     * Creates a [ScreenViewFactory] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show renderings of type [RenderingT] : [Screen], using a [ScreenViewUpdater]
     * created by [constructor]. Handy if you need to perform some set up before
     * [showRendering] is called.
     *
     *   class HelloScreenRunner(
     *     private val binding: HelloGoodbyeViewBinding
     *   ) : ScreenViewUpdater<HelloScreen> {
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
      noinline constructor: (BindingT) -> ScreenViewUpdater<RenderingT>
    ): ScreenViewFactory<RenderingT> =
      ViewBindingScreenViewFactory(RenderingT::class, bindingInflater, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to show renderings of
     * type [RenderingT]  : [Screen], using a [ScreenViewUpdater] created by [constructor].
     * Avoids any use of [AndroidX ViewBinding][ViewBinding].
     */
    public inline fun <reified RenderingT : Screen> bind(
      @LayoutRes layoutId: Int,
      noinline constructor: (View) -> ScreenViewUpdater<RenderingT>
    ): ScreenViewFactory<RenderingT> =
      LayoutScreenViewFactory(RenderingT::class, layoutId, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to "show" renderings of type [RenderingT],
     * with a no-op [ScreenViewUpdater]. Handy for showing static views, e.g. when prototyping.
     */
    @Suppress("unused")
    public inline fun <reified RenderingT : Screen> bindNoRunner(
      @LayoutRes layoutId: Int
    ): ScreenViewFactory<RenderingT> = bind(layoutId) { ScreenViewUpdater { _, _ -> } }
  }
}

internal fun Context.viewBindingLayoutInflater(container: ViewGroup?) =
  LayoutInflater.from(container?.context ?: this)
      .cloneInContext(this)
