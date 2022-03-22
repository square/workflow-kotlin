package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding

/**
 * A [ViewRegistry.Entry] that can build and update Android [View] instances
 * to display [Screen] renderings of a particular [type].
 *
 * It is most common to create instances via [forViewBinding], [forLayoutResource],
 * [forStaticLayoutResource] or [forBuiltView].
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactory<in ScreenT : Screen> : ViewRegistry.Entry<ScreenT> {
  public fun buildView(
    environment: ViewEnvironment,
    context: Context,
    container: ViewGroup? = null
  ): View

  public fun updateView(
    view: View,
    rendering: ScreenT,
    environment: ViewEnvironment,
  )
  public companion object {
    /**
     * Creates a [ScreenViewFactory] that [inflates][bindingInflater] a [ViewBinding] ([BindingT])
     * to show renderings of type [RenderingT] : [Screen], using [a lambda][showRendering].
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
    public inline fun <BindingT : ViewBinding, reified RenderingT : Screen> forViewBinding(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      crossinline showRendering: BindingT.(RenderingT, ViewEnvironment) -> Unit
    ): ScreenViewFactory<RenderingT> = forViewBinding(bindingInflater) { binding ->
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
     *     companion object : ScreenViewFactory<HelloScreen> by forViewBinding(
     *       HelloGoodbyeViewBinding::inflate, ::HelloScreenRunner
     *     )
     *   }
     *
     * If the view doesn't need to be initialized before [showRendering] is called,
     * use the variant above which just takes a lambda.
     */
    public inline fun <BindingT : ViewBinding, reified RenderingT : Screen> forViewBinding(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      noinline constructor: (BindingT) -> ScreenViewRunner<RenderingT>
    ): ScreenViewFactory<RenderingT> =
      ViewBindingScreenViewFactory(RenderingT::class, bindingInflater, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to show renderings of
     * type [RenderingT], using a [ScreenViewRunner] created by [constructor].
     * Avoids any use of [AndroidX ViewBinding][ViewBinding].
     */
    public inline fun <reified RenderingT : Screen> forLayoutResource(
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
    public inline fun <reified RenderingT : Screen> forStaticLayoutResource(
      @LayoutRes layoutId: Int
    ): ScreenViewFactory<RenderingT> = forLayoutResource(layoutId) { ScreenViewRunner { _, _ -> } }

    /**
     * Creates a [ScreenViewFactory] that uses [buildViewAndRunner] to create both a
     * [View] and a mated [ScreenViewRunner] to handle calls to [ScreenViewFactory.updateView].
     */
    public inline fun <reified RenderingT : Screen> forBuiltView(
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

@WorkflowUiExperimentalApi
public inline fun <reified ScreenT : Screen> ScreenViewFactory(
  crossinline buildView: (ViewEnvironment, Context, ViewGroup?) -> View,
  crossinline updateView: (View, ScreenT, ViewEnvironment) -> Unit
): ScreenViewFactory<ScreenT> {
  return object : ScreenViewFactory<ScreenT> {
    override val type = ScreenT::class

    override fun buildView(
      environment: ViewEnvironment,
      context: Context,
      container: ViewGroup?
    ) = buildView(environment, context, container)

    override fun updateView(
      view: View,
      rendering: ScreenT,
      environment: ViewEnvironment
    ) = updateView(view, rendering, environment)
  }
}

/**
 * Use the [ScreenViewFactoryFinder] in [environment] to return the [ScreenViewFactory]
 * bound to the type of the receiving [Screen].
 *
 * - It is more common to use [WorkflowViewStub.show] than to call this method directly
 * - Call [ScreenViewFactory.start] to create and initialize a new [View]
 * - If you don't particularly need to mess with the [ScreenViewFactory] before creating
 *   a view, use [Screen.toView] instead of this method.
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.toViewFactory(
  environment: ViewEnvironment
): ScreenViewFactory<ScreenT> {
  return environment[ScreenViewFactoryFinder].getViewFactoryForRendering(environment, this)
}

/**
 * Creates a [ScreenViewHolder] wrapping a [View] able to display [initialRendering],
 * and initializes the view.
 *
 * By default "initialize" means making the first call to [ScreenViewHolder.show].
 * To add more initialization behavior (typically a call to [WorkflowLifecycleOwner.installOn]),
 * provide a [viewStarter].
 */
@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewFactory<ScreenT>.start(
  initialRendering: ScreenT,
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  viewStarter: ViewStarter? = null
): ScreenViewHolder<ScreenT> {
  return ScreenViewHolder(
    this,
    initialViewEnvironment,
    initialRendering,
    buildView(initialViewEnvironment, contextForNewView, container),
  ).also { holder ->
    val resolvedStarter = viewStarter ?: ViewStarter { _, doStart -> doStart() }

    val legacyStarter: ((View) -> Unit)? = holder.view.starterOrNull

    if (legacyStarter != null) {
      var shown = false
      // This View was built by a legacy ViewFactory, and so it needs to be
      // started in just the right way.
      //
      // The tricky bit is the old starter's default value, a function that calls
      // View.showRendering(). Odds are it's wrapped and wrapped again deep inside
      // legacyStarter. To ensure it gets called at the right time, and that we don't
      // update the view redundantly, we use bindShowRendering to replace View.showRendering()
      // with a call to our own holder.show(). (No need to call the original showRendering(),
      // AsScreenViewFactory blanked it.)
      //
      // This same call to bindShowRendering will also update View.getRendering() and
      // View.environment() to return what was passed in here, as expected.
      holder.view.bindShowRendering(
        initialRendering, initialViewEnvironment
      ) { rendering, environment ->
        holder.show(rendering, environment)
        shown = true
      }
      holder.view.starter = { startingView ->
        resolvedStarter.startView(startingView) { legacyStarter(startingView) }
      }
      // We have to call View.start() to fire this off rather than calling the starter directly,
      // to keep the rest of the legacy machinery happy.
      holder.view.start()
      check(shown) {
        "A ViewStarter provided to ViewRegistry.buildView or a DecorativeViewFactory " +
          "neglected to call the given doStart() function"
      }
    } else {
      var shown = false
      resolvedStarter.startView(holder.view) {
        holder.show(initialRendering, initialViewEnvironment)
        shown = true
      }
      check(shown) {
        "A ViewStarter provided to Screen.toView or ScreenViewFactory.start " +
          "neglected to call the given doStart() function"
      }
    }
  }
}

/**
 * Creates a [View] able to display [initialRendering], and initializes it. By
 * default "initialize" means making the first call to [ScreenViewFactory.updateView].
 * To add more initialization behavior (typically a call to [WorkflowLifecycleOwner.installOn]),
 * provide a [viewStarter].
 *
 * This method is purely shorthand for calling [Screen.toViewFactory] and then
 * [ScreenViewFactory.start]. You might wish to make those calls separately if you
 * need to treat the [ScreenViewFactory] before using it.
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.toView(
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  viewStarter: ViewStarter? = null
): ScreenViewHolder<ScreenT> {
  return toViewFactory(initialViewEnvironment)
    .start(this, initialViewEnvironment, contextForNewView, container, viewStarter)
}

/**
 * A wrapper for the function invoked when [ScreenViewFactory.start] or
 * [Screen.toView] is called, allowing for custom initialization of
 * a newly built [View] before or after the first call to [ScreenViewFactory.updateView].
 */
@WorkflowUiExperimentalApi
public fun interface ViewStarter {
  /** Called from [View.start]. [doStart] must be invoked. */
  public fun startView(
    view: View,
    doStart: () -> Unit
  )
}
