package com.squareup.workflow1.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import com.squareup.workflow1.ui.ScreenViewRunner.Companion.bind
import com.squareup.workflow1.ui.ScreenViewRunner.Companion.bindBuiltView

@WorkflowUiExperimentalApi
public typealias ViewBindingInflater<BindingT> = (LayoutInflater, ViewGroup?, Boolean) -> BindingT

@WorkflowUiExperimentalApi
public typealias ViewAndRunnerBuilder<T> =
  (ViewEnvironment, Context, ViewGroup?) -> Pair<View, ScreenViewRunner<T>>

/**
 * An object that manages a [View] instance built by a [ScreenViewFactory.buildView], providing
 * continuity between calls to [ScreenViewFactory.updateView]. A [ScreenViewRunner]
 * is instantiated when its [View] is built -- there is a 1:1 relationship between a [View]
 * and the [ScreenViewRunner] that drives it.
 *
 * Note that use of [ScreenViewRunner] is not required by [ScreenViewFactory]. [ScreenViewRunner]
 * is just a convenient bit of glue for working with [AndroidX ViewBinding][ViewBinding], XML
 * layout resources, etc.
 *
 * Use a [bind] function to tie a [ScreenViewRunner] implementation to a [ScreenViewFactory]
 * derived from an Android [ViewBinding], XML layout resource, or [factory function][bindBuiltView].
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
     * to show renderings of type [RenderingT], using a [ScreenViewRunner]
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
    // TODO: all these bind names are terrible, and these functions should move
    //  to ScreenViewFactory.Companion
    public inline fun <BindingT : ViewBinding, reified RenderingT : Screen> bind(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      noinline constructor: (BindingT) -> ScreenViewRunner<RenderingT>
    ): ScreenViewFactory<RenderingT> =
      ViewBindingScreenViewFactory(RenderingT::class, bindingInflater, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to show renderings of
     * type [RenderingT], using a [ScreenViewRunner] created by [constructor].
     * Avoids any use of [AndroidX ViewBinding][ViewBinding].
     */
    public inline fun <reified RenderingT : Screen> bind(
      @LayoutRes layoutId: Int,
      noinline constructor: (View) -> ScreenViewRunner<RenderingT>
    ): ScreenViewFactory<RenderingT> =
      LayoutScreenViewFactory(RenderingT::class, layoutId, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to "show" renderings of type
     * [RenderingT], but never updates the created view. Handy for showing static displays,
     * e.g. when prototyping.
     */
    @Suppress("unused")
    public inline fun <reified RenderingT : Screen> bindNoRunner(
      @LayoutRes layoutId: Int
    ): ScreenViewFactory<RenderingT> = bind(layoutId) { ScreenViewRunner { _, _ -> } }

    /**
     * Creates a [ScreenViewFactory] that uses [buildViewAndRunner] to create both a
     * [View] and a mated [ScreenViewRunner] to handle calls to [ScreenViewFactory.updateView].
     */
    public inline fun <reified RenderingT : Screen> bindBuiltView(
      crossinline buildViewAndRunner: ViewAndRunnerBuilder<RenderingT>,
    ): ScreenViewFactory<RenderingT> = ScreenViewFactory(
      buildView = { environment, context, container ->
        val (view: View, runner: ScreenViewRunner<RenderingT>) =
          buildViewAndRunner(environment, context, container)
        view.setViewRunner(runner)
        view
      },
      updateView = { view, rendering, environment ->
        view.getViewRunner<RenderingT>().showRendering(rendering, environment)
      }
    )
  }
}

internal fun Context.viewBindingLayoutInflater(container: ViewGroup?) =
  LayoutInflater.from(container?.context ?: this)
    .cloneInContext(this)
