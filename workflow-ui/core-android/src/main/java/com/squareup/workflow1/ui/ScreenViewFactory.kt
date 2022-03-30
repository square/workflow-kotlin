package com.squareup.workflow1.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.forBuiltView
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.forLayoutResource
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.forStaticLayoutResource
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.forViewBinding

@WorkflowUiExperimentalApi
public typealias ViewBindingInflater<BindingT> = (LayoutInflater, ViewGroup?, Boolean) -> BindingT

/**
 * The function that updates a [View] instance built by a [ScreenViewFactory].
 * Each [ScreenViewRunner] instance is paired with the single [View] instance,
 * its neighbor in a [ScreenViewHolder].
 *
 * Use [forLayoutResource], [forViewBinding], etc., to create a [ScreenViewFactory]
 * from a [ScreenViewRunner].
 */
@WorkflowUiExperimentalApi
public fun interface ScreenViewRunner<in ScreenT : Screen> {
  public fun showRendering(
    rendering: ScreenT,
    viewEnvironment: ViewEnvironment
  )
}

/**
 * A [ViewRegistry.Entry] that can build Android [View] instances, along with functions
 * that can update them to display [Screen] renderings of a particular [type], bundled
 * together in instances of [ScreenViewHolder].
 *
 * It is most common to create instances via [forViewBinding], [forLayoutResource],
 * [forStaticLayoutResource] or [forBuiltView].
 *
 * It is rare to call [buildView] directly. Instead the most common path is to pass [Screen]
 * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] machinery
 * for you.
 *
 * If you are building a custom container and [WorkflowViewStub] is too restrictive,
 * use [Screen.buildView], or [ScreenViewFactory.start]. [start] is the fundamental
 * method, responsible for making the initial call to [ScreenViewHolder.show], and
 * applying any [ViewStarter] provided for custom initialization.
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactory<in ScreenT : Screen> : ViewRegistry.Entry<ScreenT> {
  public fun buildView(
    initialRendering: ScreenT,
    initialEnvironment: ViewEnvironment,
    context: Context,
    container: ViewGroup? = null
  ): ScreenViewHolder<ScreenT>

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
    public inline fun <BindingT : ViewBinding, reified ScreenT : Screen> forViewBinding(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      crossinline showRendering: BindingT.(ScreenT, ViewEnvironment) -> Unit
    ): ScreenViewFactory<ScreenT> = forViewBinding(bindingInflater) { binding ->
      ScreenViewRunner { rendering, viewEnvironment ->
        binding.showRendering(rendering, viewEnvironment)
      }
    }

    /**
     * Creates a [ScreenViewFactory] that [inflates][bindingInflater] a
     * [ViewBinding] (of type [BindingT]) to show renderings of type [ScreenT],
     * using a [ScreenViewRunner] created by [constructor]. Handy if you need
     * to perform some set up before [ScreenViewRunner.showRendering] is called.
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
    public inline fun <BindingT : ViewBinding, reified ScreenT : Screen> forViewBinding(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      noinline constructor: (BindingT) -> ScreenViewRunner<ScreenT>
    ): ScreenViewFactory<ScreenT> =
      ViewBindingScreenViewFactory(ScreenT::class, bindingInflater, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to show renderings of
     * type [ScreenT], using a [ScreenViewRunner] created by [constructor] to update it.
     * Avoids any use of [AndroidX ViewBinding][ViewBinding].
     */
    public inline fun <reified ScreenT : Screen> forLayoutResource(
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
    ): ScreenViewFactory<ScreenT> = forLayoutResource(layoutId) { ScreenViewRunner { _, _ -> } }

    /**
     * Creates a [ScreenViewFactory] that builds [View] instances entirely from code,
     * using a [ScreenViewRunner] created by [constructor] to update it.
     */
    @WorkflowUiExperimentalApi
    public inline fun <reified ScreenT : Screen> forBuiltView(
      crossinline buildView: (
        initialRendering: ScreenT,
        initialEnvironment: ViewEnvironment,
        context: Context,
        container: ViewGroup?
      ) -> ScreenViewHolder<ScreenT>,
    ): ScreenViewFactory<ScreenT> {
      return object : ScreenViewFactory<ScreenT> {
        override val type = ScreenT::class

        override fun buildView(
          initialRendering: ScreenT,
          initialEnvironment: ViewEnvironment,
          context: Context,
          container: ViewGroup?
        ): ScreenViewHolder<ScreenT> =
          buildView(initialRendering, initialEnvironment, context, container)
      }
    }
  }
}

/**
 * Use the [ScreenViewFactoryFinder] in [environment] to return the [ScreenViewFactory]
 * bound to the type of the receiving [Screen].
 *
 * - It is more common to use [WorkflowViewStub.show] than to call this method directly
 * - Call [ScreenViewFactory.start] to create and initialize a new [View]
 * - If you don't particularly need to mess with the [ScreenViewFactory] before creating
 *   a view, use [Screen.buildView] instead of this method.
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.toViewFactory(
  environment: ViewEnvironment
): ScreenViewFactory<ScreenT> {
  return environment[ScreenViewFactoryFinder].getViewFactoryForRendering(environment, this)
}

/**
 * It is more common to use [WorkflowViewStub.show] than to call this method directly.
 *
 * Creates a [ScreenViewHolder] wrapping a [View] able to display [initialRendering],
 * and initializes the view.
 *
 * By default "initialize" makes the first call to [ScreenViewHolder.show].
 * To add more initialization behavior (typically a call to [WorkflowLifecycleOwner.installOn]),
 * provide a [viewStarter].
 */
@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewFactory<ScreenT>.start(
  initialRendering: ScreenT,
  initialEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  viewStarter: ViewStarter? = null
): ScreenViewHolder<ScreenT> {
  return buildView(
    initialRendering,
    initialEnvironment,
    contextForNewView,
    container
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
        initialRendering, initialEnvironment
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
        holder.show(initialRendering, initialEnvironment)
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
 * It is more common to use [WorkflowViewStub.show] than to call this method directly.
 *
 * Creates a [View] able to display [initialRendering], and initializes it. By
 * default "initialize" makes the first call to [ScreenViewHolder.show].
 * To add more initialization behavior (typically a call to [WorkflowLifecycleOwner.installOn]),
 * provide a [viewStarter].
 *
 * This method is purely shorthand for calling [Screen.toViewFactory] and then
 * [ScreenViewFactory.start]. You might wish to make those calls separately if you
 * need to treat the [ScreenViewFactory] before using it, e.g. via [unwrapping].
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.buildView(
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
 * [Screen.buildView] is called, allowing for custom initialization of
 * a newly built [View] before or after the first call to [ScreenViewHolder.show].
 */
@WorkflowUiExperimentalApi
public fun interface ViewStarter {
  /** Called from [View.start]. [doStart] must be invoked. */
  public fun startView(
    view: View,
    doStart: () -> Unit
  )
}

/**
 * Transforms a [ScreenViewFactory] of [WrappedT] into one that can handle
 * instances of [WrapperT].
 *
 * @param unwrap a function to extract [WrappedT] instances from [WrapperT]s.
 */
@WorkflowUiExperimentalApi
public inline
fun <reified WrapperT : Screen, WrappedT : Screen> ScreenViewFactory<WrappedT>.unwrapping(
  crossinline unwrap: (wrapperScreen: WrapperT) -> WrappedT,
): ScreenViewFactory<WrapperT> = unwrapping(unwrap) { _, ws, e, su -> su(unwrap(ws), e) }

/**
 * Transforms a [ScreenViewFactory] of [WrappedT] into one that can handle
 * instances of [WrapperT].
 *
 * @param unwrap a function to extract [WrappedT] instances from [WrapperT]s.
 *
 * @param showWrapperScreen a function invoked when an instance of [WrapperT] needs
 * to be shown in a [View] built to display instances of [WrappedT]. It allows
 * pre- and post-processing of both the [View] and the [ViewEnvironment].
 */
@WorkflowUiExperimentalApi
public inline
fun <reified WrapperT : Screen, WrappedT : Screen> ScreenViewFactory<WrappedT>.unwrapping(
  crossinline unwrap: (wrapperScreen: WrapperT) -> WrappedT,
  crossinline showWrapperScreen: (
    view: View,
    wrapperScreen: WrapperT,
    environment: ViewEnvironment,
    showUnwrappedScreen: (WrappedT, ViewEnvironment) -> Unit
  ) -> Unit
): ScreenViewFactory<WrapperT> {
  val wrappedFactory = this

  return object : ScreenViewFactory<WrapperT>
  by forBuiltView(
    buildView = { initialRendering, initialEnvironment, context, container ->
      val wrappedHolder = wrappedFactory.buildView(
        unwrap(initialRendering), initialEnvironment, context, container
      )

      object : ScreenViewHolder<WrapperT> {
        override val view = wrappedHolder.view
        override val environment: ViewEnvironment get() = wrappedHolder.environment

        override val runner: ScreenViewRunner<WrapperT> =
          ScreenViewRunner { wrapperScreen, newEnvironment ->
            showWrapperScreen(view, wrapperScreen, newEnvironment) { unwrappedScreen, env ->
              wrappedHolder.runner.showRendering(unwrappedScreen, env)
            }
          }
      }
    }
  ) {
  }
}

internal fun Context.viewBindingLayoutInflater(container: ViewGroup?) =
  LayoutInflater.from(container?.context ?: this)
    .cloneInContext(this)
