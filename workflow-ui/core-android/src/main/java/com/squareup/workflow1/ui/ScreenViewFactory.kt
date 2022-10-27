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
     * Creates a [ScreenViewFactory] for [WrapperT] that finds and delegates to the one for
     * [WrappedT]. Allows [WrapperT] to wrap instances of [WrappedT] to add information or behavior,
     * without requiring wasteful wrapping in the view system.
     *
     * One general note: when creating a wrapper rendering, you're very likely to want it to
     * implement [Compatible], to ensure that checks made to update or replace a view are based on
     * the wrapped item. Each wrapper example below illustrates this.
     *
     * This a simpler variant of the like named function that takes three arguments, for use when
     * there is no need to manipulate the [ScreenViewHolder].
     *
     * ## Examples
     *
     * To make one rendering type an "alias" for another -- that is, to use the same
     * [ScreenViewFactory] to display it:
     *
     * class RealScreen(val data: String): AndroidScreen<RealScreen> { override val viewFactory =
     * fromLayout<RealScreen>(...) }
     *
     * class AliasScreen(val similarData: String) : AndroidScreen<AliasScreen> {
     * override val viewFactory = forWrapper<AliasScreen, RealScreen> { aliasScreen ->
     * RealScreen(aliasScreen.similarData) } }
     *
     * To make one [Screen] type a wrapper for others:
     *
     * class Wrapper<W>(val wrapped: W: Screen) : AndroidScreen<Wrapper<W>>, Compatible {
     * override val compatibilityKey = Compatible.keyFor(wrapped) override val viewFactory =
     * ScreenViewFactory.forWrapper<Wrapper<W>, W> { it.wrapped } }
     *
     * To make a wrapper that adds information to the [ViewEnvironment]:
     *
     * class ReverseNeutronFlowPolarity : ViewEnvironmentKey<Boolean>(Boolean::class) { override val
     * default = false }
     *
     * class ReversePolarityScreen<W : Screen>( val wrapped: W ) :
     * AndroidScreen<ReversePolarityScreen<W>>, Compatible { override val compatibilityKey: String
     * = Compatible.keyFor(wrapped) override val viewFactory = forWrapper<OverrideNeutronFlow<W>,
     * Screen> { it.wrapped.withEnvironment( Environment.EMPTY + (ReverseNeutronFlowPolarity to
     * true) ) } }
     *
     * @param unwrap a function to extract [WrappedT] instances from [WrapperT]s.
     */
    @WorkflowUiExperimentalApi
    public inline fun <
      reified WrapperT : Screen,
      WrappedT : Screen
      > forWrapper(
      crossinline unwrap: (wrapperScreen: WrapperT) -> WrappedT,
    ): ScreenViewFactory<WrapperT> = forWrapper(
      unwrap = unwrap,
      beforeShowing = {}
    ) { _, wrapper, e, showWrapper ->
      showWrapper(unwrap(wrapper), e)
    }

    /**
     * Creates a [ScreenViewFactory] for [WrapperT] that finds and delegates to the one for
     * [WrappedT]. Allows [WrapperT] to wrap instances of [WrappedT] to add information or behavior,
     * without requiring wasteful wrapping in the view system.
     *
     * This fully featured variant of the function is able to initialize the freshly created
     * [ScreenViewHolder], and transform the wrapped [ScreenViewHolder.runner].
     *
     * To make a wrapper that customizes [View] initialization:
     *
     * class WithTutorialTips<W : Screen>( val wrapped: W ) : AndroidScreen<WithTutorialTips<W>>,
     * Compatible { override val compatibilityKey = Compatible.keyFor(wrapped) override
     * val viewFactory = forWrapper<WithTutorialTips<W>, W>( unwrap = { it.wrapped },
     * beforeShowing = { TutorialTipRunner.initialize(it.view) }, showWrapperScreen = { _,
     * wrapper, environment, showWrapper -> showWrapper(unwrap(wrapper), environment) } ) }
     *
     * @param unwrap a function to extract [WrappedT] instances from [WrapperT]s.
     * @param beforeShowing a function to be invoked immediately after a new [View] is built.
     * @param showWrapperScreen a function to be invoked when an instance of [WrapperT] needs to be
     *     shown in a [View] built to display instances of [WrappedT]. Allows pre- and
     *     post-processing of the [View].
     */
    @WorkflowUiExperimentalApi
    public inline fun <
      reified WrapperT : Screen,
      WrappedT : Screen
      > forWrapper(
      crossinline unwrap: (wrapperScreen: WrapperT) -> WrappedT,
      crossinline beforeShowing: (viewHolder: ScreenViewHolder<WrapperT>) -> Unit = {},
      crossinline showWrapperScreen: (
        view: View,
        wrapperScreen: WrapperT,
        environment: ViewEnvironment,
        showUnwrappedScreen: (WrappedT, ViewEnvironment) -> Unit
      ) -> Unit,
    ): ScreenViewFactory<WrapperT> =
      fromCode { initialRendering, initialEnvironment, context, container ->
        val wrappedFactory = unwrap(initialRendering).toViewFactory(initialEnvironment)
        val wrapperFactory = wrappedFactory.toUnwrappingViewFactory(unwrap, showWrapperScreen)
        wrapperFactory.buildView(
          initialRendering,
          initialEnvironment,
          context,
          container
        ).also { beforeShowing(it) }
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
public inline fun <
  reified WrapperT : Screen,
  WrappedT : Screen
  > ScreenViewFactory<WrappedT>.toUnwrappingViewFactory(
  crossinline unwrap: (wrapperScreen: WrapperT) -> WrappedT
): ScreenViewFactory<WrapperT> {
  return toUnwrappingViewFactory(unwrap) { _, wrapperScreen, environment, showUnwrappedScreen ->
    showUnwrappedScreen(unwrap(wrapperScreen), environment)
  }
}

/**
 * Transforms a [ScreenViewFactory] of [WrappedT] into one that can handle instances of [WrapperT].
 *
 * @see [ScreenViewFactory.forWrapper].
 */
@WorkflowUiExperimentalApi
public inline fun <
  reified WrapperT : Screen,
  WrappedT : Screen
  > ScreenViewFactory<WrappedT>.toUnwrappingViewFactory(
  crossinline unwrap: (wrapperScreen: WrapperT) -> WrappedT,
  crossinline showWrapperScreen: (
    view: View,
    wrapperScreen: WrapperT,
    environment: ViewEnvironment,
    showUnwrappedScreen: (WrappedT, ViewEnvironment) -> Unit
  ) -> Unit
): ScreenViewFactory<WrapperT> {
  val wrappedFactory = this

  return object : ScreenViewFactory<WrapperT> by fromCode(
    buildView = { initialRendering, initialEnvironment, context, container ->
      val wrappedHolder = wrappedFactory.buildView(
        unwrap(initialRendering),
        initialEnvironment,
        context,
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
