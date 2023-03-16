package com.squareup.workflow1.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromCode
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromLayout
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromViewBinding

@WorkflowUiExperimentalApi
public typealias ViewBindingInflater<BindingT> = (LayoutInflater, ViewGroup?, Boolean) -> BindingT

/**
 * A [ViewRegistry.Entry] that can build Android [View] instances, along with functions that can
 * update them to display [Screen] renderings of a particular [type], bundled together in instances
 * of [ScreenViewHolder].
 *
 * Use [fromLayout], [fromViewBinding], etc., to create a [ScreenViewFactory]. These helper methods
 * take a layout resource, view binding, or view building function as arguments, along with a
 * factory to create a [showRendering] [ScreenViewRunner.showRendering] function.
 *
 * It is rare to call [buildView] directly. Instead the most common path is to pass [Screen]
 * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] machinery for you.
 *
 * If you are building a custom container and [WorkflowViewStub] is too restrictive, use
 * [ScreenViewFactory.startShowing].
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactory<in ScreenT : Screen> : ViewRegistry.Entry<ScreenT> {
  /**
   * It is rare to call this method directly. Instead the most common path is to pass [Screen]
   * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] machinery for
   * you.
   *
   * Called by [startShowing] to create a [ScreenViewHolder] wrapping a [View] able to display a
   * stream of [ScreenT] renderings, starting with [initialRendering].
   */
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
     * val HelloViewFactory: ScreenViewFactory<HelloScreen> =
     * forViewBinding(HelloGoodbyeViewBinding::inflate) { rendering, _ -> helloMessage.text
     * = rendering.message helloMessage.setOnClickListener { rendering.onClick(Unit) } }
     *
     * If you need to initialize your view before [showRendering] is called, implement
     * [ScreenViewRunner] and create a binding using the `forViewBinding` variant that accepts a
     * `(ViewBinding) -> ScreenViewRunner` function, below.
     */
    public inline fun <BindingT : ViewBinding, reified ScreenT : Screen> fromViewBinding(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      crossinline showRendering: BindingT.(ScreenT, ViewEnvironment) -> Unit
    ): ScreenViewFactory<ScreenT> = fromViewBinding(bindingInflater) { binding ->
      ScreenViewRunner { rendering, viewEnvironment ->
        binding.showRendering(rendering, viewEnvironment)
      }
    }

    /**
     * Creates a [ScreenViewFactory] that [inflates][bindingInflater] a [ViewBinding] (of
     * type [BindingT]) to show renderings of type [ScreenT], using a [ScreenViewRunner]
     * created by [constructor]. Handy if you need to perform some set up before
     * [ScreenViewRunner.showRendering] is called.
     *
     * class HelloScreenRunner( private val binding: HelloGoodbyeViewBinding ) :
     * ScreenViewRunner<HelloScreen> {
     *
     * override fun showRendering(rendering: HelloScreen) { binding.messageView.text =
     * rendering.message binding.messageView.setOnClickListener { rendering.onClick(Unit) } }
     *
     * companion object : ScreenViewFactory<HelloScreen> by forViewBinding(
     * HelloGoodbyeViewBinding::inflate, ::HelloScreenRunner ) }
     *
     * If the view doesn't need to be initialized before [showRendering] is called, use the variant
     * above which just takes a lambda.
     */
    public inline fun <BindingT : ViewBinding, reified ScreenT : Screen> fromViewBinding(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      noinline constructor: (BindingT) -> ScreenViewRunner<ScreenT>
    ): ScreenViewFactory<ScreenT> =
      ViewBindingScreenViewFactory(ScreenT::class, bindingInflater, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to show renderings of type [ScreenT],
     * using a [ScreenViewRunner] created by [constructor] to update it. Avoids any use of
     * [AndroidX ViewBinding][ViewBinding].
     */
    public inline fun <reified ScreenT : Screen> fromLayout(
      @LayoutRes layoutId: Int,
      noinline constructor: (View) -> ScreenViewRunner<ScreenT>
    ): ScreenViewFactory<ScreenT> =
      LayoutScreenViewFactory(ScreenT::class, layoutId, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to "show" renderings of type
     * [ScreenT], but never updates the created view. Handy for showing static displays, e.g. when
     * prototyping.
     */
    @Suppress("unused")
    public inline fun <reified ScreenT : Screen> fromStaticLayout(
      @LayoutRes layoutId: Int
    ): ScreenViewFactory<ScreenT> = fromLayout(layoutId) { ScreenViewRunner { _, _ -> } }

    /**
     * Creates a [ScreenViewFactory] that builds [View] instances entirely from code, using a
     * [ScreenViewRunner] created by [buildView] to update it.
     */
    @WorkflowUiExperimentalApi
    public inline fun <reified ScreenT : Screen> fromCode(
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

    /**
     * Creates a [ScreenViewFactory] for type [WrapperT] that finds and delegates to the one for
     * [WrappedT]. Allows [WrapperT] to add information or behavior, without requiring wasteful
     * parallel wrapping in the view system.
     *
     * ## Examples
     *
     * To make one [Screen] type a wrapper for others:
     *
     *    class MyWrapper<W : Screen>(
     *      override val content: W
     *    ) : AndroidScreen<Wrapper<W>>, Wrapper<Screen, W> {
     *      override val viewFactory = forWrapper<MyWrapper<W>, W>()
     *
     *      override fun <U : Screen> map(transform: (W) -> U) =
     *        MyWrapper(transform(content))
     *    }
     *
     * To make a wrapper that customizes [View] initialization:
     *
     *    class WithTutorialTips<W : Screen>(
     *      override val content: W
     *    ) : AndroidScreen<WithTutorialTips<W>>, Wrapper<Screen, W> {
     *      override val viewFactory = forWrapper<WithTutorialTips<W>, W>(
     *        beforeShowing = { TutorialTipRunner.initialize(it.view) }
     *      )
     *
     *      override fun <U : Screen> map(transform: (W) -> U) =
     *        WithTutorialTips(transform(content))
     *    }
     *
     * @param prepEnvironment a function to process the initial [ViewEnvironment]
     * before the [ScreenViewFactory] is fetched. Note that this function is not
     * applied on updates. Add a [showWrapperScreen] function if you need that.
     *
     * @param prepContext a function to process the [Context] used to create each [View].
     * it is passed the product of [prepEnvironment]
     *
     * @param unwrap a function to extract [WrappedT] instances from [WrapperT]s.
     *
     * @param beforeShowing a function to be invoked immediately after a new [View] is built.
     *
     * @param showWrapperScreen a function to be invoked when an instance of [WrapperT] needs
     * to be shown in a [View] built to display instances of [WrappedT]. Allows pre-
     * and post-processing of the [View].
     */
    @WorkflowUiExperimentalApi
    public inline fun <reified WrapperT, WrappedT : Screen> forWrapper(
      crossinline prepEnvironment: (environment: ViewEnvironment) -> ViewEnvironment = { it },
      crossinline prepContext: (
        environment: ViewEnvironment,
        context: Context
      ) -> Context = { _, c -> c },
      crossinline unwrap: (wrapperScreen: WrapperT) -> WrappedT = { it.content },
      crossinline beforeShowing: (viewHolder: ScreenViewHolder<WrapperT>) -> Unit = {},
      crossinline showWrapperScreen: (
        view: View,
        wrapperScreen: WrapperT,
        environment: ViewEnvironment,
        showUnwrappedScreen: (WrappedT, ViewEnvironment) -> Unit
      ) -> Unit = { _, wrapper, e, showWrapper -> showWrapper(wrapper.content, e) },
    ): ScreenViewFactory<WrapperT> where WrapperT : Screen, WrapperT : Wrapper<Screen, WrappedT> {
      return fromCode { initialRendering, initialEnvironment, context, container ->
        val preppedEnvironment = prepEnvironment(initialEnvironment)
        val wrappedFactory = unwrap(initialRendering).toViewFactory(preppedEnvironment)
        val wrapperFactory = wrappedFactory.toUnwrappingViewFactory(
          prepEnvironment,
          prepContext,
          unwrap,
          showWrapperScreen
        )

        // Note that we give the factory the original initialEnvironment.
        // It applies prepEnvironment itself.
        wrapperFactory.buildView(initialRendering, initialEnvironment, context, container)
          .also { beforeShowing(it) }
      }
    }
  }
}

/**
 * It is rare to call this method directly. Instead the most common path is to pass [Screen]
 * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] machinery for you.
 *
 * Use the [ScreenViewFactoryFinder] in [environment] to return the [ScreenViewFactory] bound to the
 * type of the receiving [Screen]. Call [ScreenViewFactory.startShowing] to create and initialize a
 * new [View].
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.toViewFactory(
  environment: ViewEnvironment
): ScreenViewFactory<ScreenT> {
  return environment[ScreenViewFactoryFinder].getViewFactoryForRendering(environment, this)
}

/**
 * It is rare to call this method directly. Instead the most common path is to pass [Screen]
 * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] machinery for you.
 *
 * Creates a [ScreenViewHolder] wrapping a [View] able to display a stream of [ScreenT] renderings,
 * starting with [initialRendering].
 *
 * To add more initialization behavior (typically a call to
 * [WorkflowLifecycleOwner.installOn][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner.installOn]),
 * provide a [viewStarter].
 */
@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewFactory<ScreenT>.startShowing(
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
        initialRendering,
        initialEnvironment
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
        "A ViewStarter provided to ScreenViewFactory.startShowing " +
          "neglected to call the given doStart() function"
      }
    }
  }
}

/**
 * A wrapper for the function invoked when [ScreenViewFactory.startShowing] is called, allowing
 * for custom initialization of a newly built [View] before or after the first call to
 * [ScreenViewHolder.show].
 */
@WorkflowUiExperimentalApi
public fun interface ViewStarter {
  /** Called from [ScreenViewFactory.startShowing]. [doStart] must be invoked. */
  public fun startView(
    view: View,
    doStart: () -> Unit
  )
}

/**
 * Transforms a [ScreenViewFactory] of [WrappedT] into one that can handle instances of [WrapperT].
 *
 * @see [ScreenViewFactory.forWrapper].
 */
@WorkflowUiExperimentalApi
public inline fun <reified WrapperT, WrappedT> ScreenViewFactory<WrappedT>.toUnwrappingViewFactory(
  crossinline prepEnvironment: (environment: ViewEnvironment) -> ViewEnvironment = { e -> e },
  crossinline prepContext: (
    environment: ViewEnvironment,
    context: Context
  ) -> Context = { _, c -> c },
  crossinline unwrap: (wrapperScreen: WrapperT) -> WrappedT = { it.content },
  crossinline showWrapperScreen: (
    view: View,
    wrapperScreen: WrapperT,
    environment: ViewEnvironment,
    showUnwrappedScreen: (WrappedT, ViewEnvironment) -> Unit
  ) -> Unit = { _, wrapperScreen, environment, showUnwrappedScreen ->
    showUnwrappedScreen(wrapperScreen.content, environment)
  }
): ScreenViewFactory<WrapperT>
  where WrapperT : Screen, WrapperT : Wrapper<Screen, WrappedT>, WrappedT : Screen {
  val wrappedFactory = this

  return object : ScreenViewFactory<WrapperT> by fromCode(
    buildView = { initialRendering, initialEnvironment, context, container ->
      val preppedInitialEnvironment = prepEnvironment(initialEnvironment)
      val preppedContext = prepContext(preppedInitialEnvironment, context)

      val wrappedHolder = wrappedFactory.buildView(
        unwrap(initialRendering),
        preppedInitialEnvironment,
        preppedContext,
        container
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
