package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.BackStackScreenViewFactory
import com.squareup.workflow1.ui.container.BodyAndModalsContainer
import com.squareup.workflow1.ui.container.BodyAndModalsScreen
import com.squareup.workflow1.ui.container.EnvironmentScreen
import com.squareup.workflow1.ui.container.EnvironmentScreenViewFactory

/**
 * Factory for Android [View] instances that can show renderings of type [ScreenT] : [Screen].
 *
 * It's simplest to have your rendering classes implement [AndroidScreen] to associate
 * them with appropriate an appropriate [ScreenViewFactory]. For more flexibility, and to
 * avoid coupling your workflow directly to the Android runtime, see [ViewRegistry].
 *
 * - Use [ScreenViewFactory.ofViewBinding] to create a factory for an
 *    [AndroidX ViewBinding][ViewBinding]
 * - Use [ScreenViewFactory.ofLayout] or [ScreenViewFactory.ofStaticLayout] to create
 *   a factory for an XML layout resource
 * - Use [ScreenViewFactory.of] to create a factory entirely at runtime.
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactory<ScreenT : Screen> : ViewRegistry.Entry<ScreenT> {
  /**
   * Returns a [ScreenViewHolder] ready to display [initialRendering] (and any succeeding values).
   * Callers of this method must call [ScreenViewHolder.start] exactly once before
   * calling [ScreenViewHolder.showScreen].
   */
  public fun buildView(
    initialRendering: ScreenT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup? = null
  ): ScreenViewHolder<ScreenT>

  public companion object {
    /**
     * Creates a [ScreenViewFactory] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show renderings of type [ScreenT] : [Screen], using [a lambda][showRendering].
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
    public inline fun <BindingT : ViewBinding, reified ScreenT : Screen> ofViewBinding(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      crossinline showRendering: BindingT.(ScreenT, ViewEnvironment) -> Unit
    ): ScreenViewFactory<ScreenT> = ofViewBinding(bindingInflater) { binding ->
      ScreenViewUpdater { rendering, viewEnvironment ->
        binding.showRendering(rendering, viewEnvironment)
      }
    }

    /**
     * Creates a [ScreenViewFactory] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show renderings of type [ScreenT] : [Screen], using a [ScreenViewUpdater]
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
    public inline fun <BindingT : ViewBinding, reified ScreenT : Screen> ofViewBinding(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      noinline constructor: (BindingT) -> ScreenViewUpdater<ScreenT>
    ): ScreenViewFactory<ScreenT> =
      ViewBindingScreenViewFactory(ScreenT::class, bindingInflater, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to show renderings of
     * type [ScreenT]  : [Screen], using a [ScreenViewUpdater] created by [constructor].
     * Avoids any use of [AndroidX ViewBinding][ViewBinding].
     */
    public inline fun <reified ScreenT : Screen> ofLayout(
      @LayoutRes layoutId: Int,
      noinline constructor: (View) -> ScreenViewUpdater<ScreenT>
    ): ScreenViewFactory<ScreenT> =
      LayoutScreenViewFactory(ScreenT::class, layoutId, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to "show" renderings of type [ScreenT],
     * with a no-op [ScreenViewUpdater]. Handy for showing static views, e.g. when prototyping.
     */
    @Suppress("unused")
    public inline fun <reified ScreenT : Screen> ofStaticLayout(
      @LayoutRes layoutId: Int
    ): ScreenViewFactory<ScreenT> = ofLayout(layoutId) { ScreenViewUpdater { _, _ -> } }

    /** Creates a [ScreenViewFactory] entirely from code. */
    public inline fun <reified ScreenT : Screen> of(
      noinline viewConstructor: (
        initialRendering: ScreenT,
        initialViewEnvironment: ViewEnvironment,
        context: Context,
        container: ViewGroup?
      ) -> ScreenViewHolder<ScreenT>
    ): ScreenViewFactory<ScreenT> = ManualScreenViewFactory(ScreenT::class, viewConstructor)
  }
}

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * Finds a [ScreenViewFactory] to create a [View] to display the receiving [Screen].
 * The caller is responsible for calling [View.start] on the new [View]. After that,
 * [View.showRendering] can be used to update it with new renderings that
 * are [compatible] with this [Screen]. [WorkflowViewStub] takes care of this chore itself.
 *
 * @throws IllegalArgumentException if no builder can be found for type [ScreenT]
 *
 * @throws IllegalStateException if the matching [ScreenViewFactory] fails to call
 * [View.bindShowRendering] when constructing the view
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.buildView(
  viewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null
): ScreenViewHolder<ScreenT> {
  return viewEnvironment.getViewFactoryForRendering(this).buildView(
    this, viewEnvironment, contextForNewView, container
  )
}

@WorkflowUiExperimentalApi
internal fun <ScreenT : Screen>
  ViewEnvironment.getViewFactoryForRendering(rendering: ScreenT): ScreenViewFactory<ScreenT> {
  val entry = get(ViewRegistry).getEntryFor(rendering::class)

  @Suppress("UNCHECKED_CAST", "DEPRECATION")
  return (entry as? ScreenViewFactory<ScreenT>)
    ?: (rendering as? AndroidScreen<*>)?.viewFactory as? ScreenViewFactory<ScreenT>
    ?: (rendering as? AsScreen<*>)?.let { AsScreenViewFactory as ScreenViewFactory<ScreenT> }
    ?: (rendering as? BackStackScreen<*>)?.let {
      BackStackScreenViewFactory as ScreenViewFactory<ScreenT>
    }
    ?: (rendering as? BodyAndModalsScreen<*, *>)?.let {
      BodyAndModalsContainer as ScreenViewFactory<ScreenT>
    }
    ?: (rendering as? NamedScreen<*>)?.let { NamedScreenViewFactory as ScreenViewFactory<ScreenT> }
    ?: (rendering as? EnvironmentScreen<*>)?.let {
      EnvironmentScreenViewFactory as ScreenViewFactory<ScreenT>
    }
    ?: throw IllegalArgumentException(
      "A ScreenViewFactory should have been registered to display $rendering, " +
        "or that class should implement AndroidScreen. Instead found $entry."
    )
}
