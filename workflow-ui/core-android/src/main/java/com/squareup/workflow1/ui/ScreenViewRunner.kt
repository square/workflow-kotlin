package com.squareup.workflow1.ui

import android.view.View
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding

/** The function that updates a [View] instance built by a [ScreenViewFactory]. */
@WorkflowUiExperimentalApi
public fun interface ScreenViewRunner<in ScreenT : Screen> {
  public fun showRendering(
    rendering: ScreenT,
    viewEnvironment: ViewEnvironment
  )

  public companion object {
    /**
     * Creates a [ScreenViewFactory] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show renderings of type [ScreenT] : [Screen], using [a lambda][showRendering].
     *
     *    val HelloViewFactory: ScreenViewFactory<HelloScreen> =
     *      forViewBinding(HelloGoodbyeViewBinding::inflate) { rendering, _ ->
     *        helloMessage.text = rendering.message
     *        helloMessage.setOnClickListener { rendering.onClick(Unit) }
     *      }
     *
     * If you need to initialize your view before [showRendering] is called,
     * implement [ScreenViewRunner] and create a binding using the `forViewBinding` variant
     * that accepts a `(ViewBinding) -> ScreenViewRunner` function, below.
     */
    public inline fun <BindingT : ViewBinding, reified ScreenT : Screen> bind(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      crossinline showRendering: BindingT.(ScreenT, ViewEnvironment) -> Unit
    ): ScreenViewFactory<ScreenT> = bind(bindingInflater) { binding ->
      ScreenViewRunner { rendering, viewEnvironment ->
        binding.showRendering(rendering, viewEnvironment)
      }
    }

    /**
     * Creates a [ScreenViewFactory] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show renderings of type [ScreenT], using a [ScreenViewRunner]
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
     *     companion object : ScreenViewFactory<HelloScreen> by forViewBinding(
     *       HelloGoodbyeViewBinding::inflate, ::HelloScreenRunner
     *     )
     *   }
     *
     * If the view doesn't need to be initialized before [showRendering] is called,
     * use the variant above which just takes a lambda.
     */
    public inline fun <BindingT : ViewBinding, reified ScreenT : Screen> bind(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      noinline constructor: (BindingT) -> ScreenViewRunner<ScreenT>
    ): ScreenViewFactory<ScreenT> =
      ViewBindingScreenViewFactory(ScreenT::class, bindingInflater, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to show renderings of
     * type [ScreenT], using a [ScreenViewRunner] created by [constructor].
     * Avoids any use of [AndroidX ViewBinding][ViewBinding].
     */
    public inline fun <reified ScreenT : Screen> bind(
      @LayoutRes layoutId: Int,
      noinline constructor: (View) -> ScreenViewRunner<ScreenT>
    ): ScreenViewFactory<ScreenT> =
      LayoutScreenViewFactory(ScreenT::class, layoutId, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to "show" renderings of type
     * [ScreenT], but never updates the created view. Handy for showing static displays,
     * e.g. when prototyping.
     */
    @Suppress("unused")
    public inline fun <reified ScreenT : Screen> forStaticLayoutResource(
      @LayoutRes layoutId: Int
    ): ScreenViewFactory<ScreenT> = bind(layoutId) { ScreenViewRunner { _, _ -> } }
  }
}
