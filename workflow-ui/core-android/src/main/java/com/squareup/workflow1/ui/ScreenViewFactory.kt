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
 * The function that updates a [View] instance built by a [ScreenViewFactory].
 * Each [ScreenViewRunner] instance is paired with the single [View] instance,
 * its neighbor in a [ScreenViewHolder].
 *
 * This is the interface you'll implement directly to update Android view code
 * from your [Screen] renderings. A [ScreenViewRunner] serves as the strategy
 * object of a [ScreenViewHolder] instantiated by a [ScreenViewFactory] -- the
 * runner provides the implmenetation for the holder's [ScreenViewHolder.show]
 * method.
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
 * Use [fromLayout], [fromViewBinding], etc., to create a [ScreenViewFactory].
 * These helper methods take a layout resource, view binding, or view building
 * function as arguments, along with a factory to create a [showRendering]
 * [ScreenViewRunner.showRendering] function.
 *
 * It is rare to call [buildView] directly. Instead the most common path is to pass [Screen]
 * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] machinery
 * for you.
 *
 * If you are building a custom container and [WorkflowViewStub] is too restrictive,
 * use [ScreenViewFactory.startShowing].
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactory<in ScreenT : Screen> : ViewRegistry.Entry<ScreenT> {
  /**
   * It is rare to call this method directly. Instead the most common path is to pass [Screen]
   * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] machinery
   * for you.
   *
   * Called by [startShowing] to create a [ScreenViewHolder] wrapping a [View] able to
   * display a stream of [ScreenT] renderings, starting with [initialRendering].
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
    public inline fun <BindingT : ViewBinding, reified ScreenT : Screen> fromViewBinding(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      crossinline showRendering: BindingT.(ScreenT, ViewEnvironment) -> Unit
    ): ScreenViewFactory<ScreenT> = fromViewBinding(bindingInflater) { binding ->
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
    public inline fun <BindingT : ViewBinding, reified ScreenT : Screen> fromViewBinding(
      noinline bindingInflater: ViewBindingInflater<BindingT>,
      noinline constructor: (BindingT) -> ScreenViewRunner<ScreenT>
    ): ScreenViewFactory<ScreenT> =
      ViewBindingScreenViewFactory(ScreenT::class, bindingInflater, constructor)

    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to show renderings of
     * type [ScreenT], using a [ScreenViewRunner] created by [constructor] to update it.
     * Avoids any use of [AndroidX ViewBinding][ViewBinding].
     */
    public inline fun <reified ScreenT : Screen> fromLayout(
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
    public inline fun <reified ScreenT : Screen> fromStaticLayout(
      @LayoutRes layoutId: Int
    ): ScreenViewFactory<ScreenT> = fromLayout(layoutId) { ScreenViewRunner { _, _ -> } }

    /**
     * Creates a [ScreenViewFactory] that builds [View] instances entirely from code,
     * using a [ScreenViewRunner] created by [constructor] to update it.
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
  }
}

/**
 * It is rare to call this method directly. Instead the most common path is to pass [Screen]
 * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] machinery
 * for you.
 *
 * Use the [ScreenViewFactoryFinder] in [environment] to return the [ScreenViewFactory]
 * bound to the type of the receiving [Screen].
 *
 * - Call [ScreenViewFactory.startShowing] to create and initialize a new [View]
 * - If you don't particularly need to mess with the [ScreenViewFactory] before creating
 *   a view, use [Screen.startShowing] instead of this method.
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.toViewFactory(
  environment: ViewEnvironment
): ScreenViewFactory<ScreenT> {
  return environment[ScreenViewFactoryFinder].getViewFactoryForRendering(environment, this)
}

/**
 * It is rare to call this method directly. Instead the most common path is to pass [Screen]
 * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] machinery
 * for you.
 *
 * Creates a [ScreenViewHolder] wrapping a [View] able to display a stream
 * of [ScreenT] renderings, starting with [initialRendering].
 *
 * To add more initialization behavior (typically a call to [WorkflowLifecycleOwner.installOn]),
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
        "A ViewStarter provided to ScreenViewFactory.startShowing " +
          "neglected to call the given doStart() function"
      }
    }
  }
}

/**
 * A wrapper for the function invoked when [ScreenViewFactory.startShowing] is called,
 * allowing for custom initialization of a newly built [View] before or after the first
 * call to [ScreenViewHolder.show].
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
 * instances of [WrapperT]. Allows [WrapperT] to wrap instances of [WrappedT]
 * to add information or behavior, without requiring wasteful wrapping in the view system.
 *
 * One general note: when creating a wrapper rendering, you're very likely to want it
 * to implement [Compatible], to ensure that checks made to update or replace a view
 * are based on the wrapped item. Each wrapper example below illustrates this.
 *
 * This a simpler variant of the like named function that takes two arguments, for
 * use when there is no need to customize the [view][ScreenViewHolder.view] or
 * the [environment][ScreenViewHolder.environment].
 *
 * ## Examples
 *
 * To make one rendering type an "alias" for another -- that is, to use the
 * same [ScreenViewFactory] to display it:
 *
 *    class RealScreen(val data: String): Screen
 *    object RealScreenViewFactory = ScreenViewFactory.fromLayout(...)
 *
 *    class AliasScreen(val similarData: String) : Screen
 *
 *    object AliasScreenViewFactory =
 *      RealScreenViewFactory.toUnwrappingViewFactory<AliasScreen, RealScreen> { aliasScreen ->
 *        RealScreen(aliasScreen.similarData)
 *      }
 *
 * To make one rendering type a wrapper for others:
 *
 *    class Wrapper<W>(val wrapped: W: Screen) : Screen, Compatible {
 *      override val compatibilityKey = Compatible.keyFor(wrapped)
 *    }
 *
 *    fun <W : Screen> WrapperViewFactory() =
 *      ScreenViewFactory.forBuiltView<Wrapper<W>> { wrapper, env, context, container ->
 *        // Get the view factory of the wrapped screen.
 *        wrapper.wrapped.toViewFactory(env)
 *          // Transform it to factory that accepts Wrapper<W>
 *          .toUnwrappingViewFactory<Wrapper<W>, W> { it.wrapped }
 *          // Delegate to the transformed factory to build the view.
 *          .buildView(wrapper, env, context, container)
 *      }
 *
 * To make a wrapper that adds information to the [ViewEnvironment]:
 *
 *    class NeutronFlowPolarity(val reversed: Boolean) {
 *      companion object : ViewEnvironmentKey<NeutronFlowPolarity>(
 *        NeutronFlowPolarity::class
 *      ) {
 *        override val default: NeutronFlowPolarity =
 *          NeutronFlowPolarity(reversed = false)
 *      }
 *    }
 *
 *    class OverrideNeutronFlow<W : Screen>(
 *      val wrapped: W,
 *      val polarity: NeutronFlowPolarity
 *    ) : Screen, Compatible {
 *      override val compatibilityKey: String = Compatible.keyFor(wrapped)
 *    }
 *
 *    fun <W : Screen> OverrideNeutronFlowViewFactory() =
 *      ScreenViewFactory.forBuiltView<OverrideNeutronFlow<W>> { wrapper, env, context, container ->
 *        // Get the view factory of the wrapped screen.
 *        wrapper.wrapped.toViewFactory(env)
 *          // Transform it to factory that accepts OverrideNeutronFlow<W>, by
 *          // replacing the OverrideNeutronFlow<W> with an EnvironmentScreen<W>
 *          .toUnwrappingViewFactory<OverrideNeutronFlow<W>, EnvironmentScreen<W>> {
 *            it.wrapped.withEnvironment(
 *              Environment.EMPTY + (NeutronFlowPolarity to it.polarity)
 *            )
 *          }
 *          // Delegate to the transformed factory to build the view.
 *          .buildView(wrapper, env, context, container)
 *      }
 *
 * @param unwrap a function to extract [WrappedT] instances from [WrapperT]s.
 */
@WorkflowUiExperimentalApi
public inline fun <
  reified WrapperT : Screen,
  WrappedT : Screen
  > ScreenViewFactory<WrappedT>.toUnwrappingViewFactory(
  crossinline unwrap: (wrapperScreen: WrapperT) -> WrappedT,
): ScreenViewFactory<WrapperT> =
  toUnwrappingViewFactory(unwrap) { _, ws, e, su -> su(unwrap(ws), e) }

/**
 * Transforms a [ScreenViewFactory] of [WrappedT] into one that can handle
 * instances of [WrapperT].
 *
 * One general note: when creating a wrapper rendering, you're very likely to want it
 * to implement [Compatible], to ensure that checks made to update or replace a view
 * are based on the wrapped item. Each wrapper example below illustrates this.
 *
 * Also see the simpler variant of this function that takes only an [unwrap] argument.
 *
 * ## Example
 *
 * To make a wrapper that customizes [View] initialization:
 *
 *    class WithTutorialTips<W : Screen>(val wrapped: W) : Screen, Compatible {
 *      override val compatibilityKey = Compatible.keyFor(wrapped)
 *    }
 *
 *    fun <W: Screen> WithTutorialTipsFactory<W>() =
 *      ScreenViewFactory.forBuiltView<WithTutorialTips<*>> = {
 *        initialRendering, initialEnv, context, container ->
 *          // Get the view factory of the wrapped screen.
 *          initialRendering.wrapped.toViewFactory(initialEnv)
 *            // Transform it to factory that accepts WithTutorialTips<W>
 *            .toUnwrappingViewFactory<WithTutorialTips<W>, W>(
 *              unwrap = { it.wrapped },
 *              showWrapperScreen = { view, withTips, env, showUnwrapped ->
 *                TutorialTipRunner.run(view)
 *                showUnwrapped(withTips.wrapped, env)
 *              }
 *              // Delegate to the transformed factory to build the view.
 *              .buildView(initialRendering, initialEnv, context, container)
 *      }
 *
 * @param unwrap a function to extract [WrappedT] instances from [WrapperT]s.
 *
 * @param showWrapperScreen a function invoked when an instance of [WrapperT] needs
 * to be shown in a [View] built to display instances of [WrappedT]. Allows
 * pre- and post-processing of the [View].
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

  return object : ScreenViewFactory<WrapperT>
  by fromCode(
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
