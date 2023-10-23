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
import com.squareup.workflow1.ui.ViewRegistry.Key
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
public typealias ViewBindingInflater<BindingT> = (LayoutInflater, ViewGroup?, Boolean) -> BindingT

/**
 * A [ViewRegistry.Entry] that can build Android [View] instances, along with functions that can
 * update them to display [Screen] renderings of a particular [type], bundled together in instances
 * of [ScreenViewHolder].
 *
 * Use [fromLayout], [fromViewBinding], etc., to create a [ScreenViewFactory]. These helper methods
 * take a layout resource, view binding, or view building function as arguments, along with a
 * factory to create a [ScreenViewRunner.showRendering] function.
 *
 * It is rare to call [buildView] directly. Instead the most common path is to pass [Screen]
 * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] machinery for you.
 *
 * If you are building a custom container and for some reason can't delegate to
 * [WorkflowViewStub], here is how to do the work yourself:
 *
 * - Call [Screen.toViewFactory], which uses the [ScreenViewFactoryFinder] in the
 *   given [ViewEnvironment] to return the [ScreenViewFactory] bound to the type
 *   of the receiving [Screen].
 *
 * - Call [ScreenViewFactory.buildView] to build a view wrapped in the [ScreenViewHolder]
 *   that manages it.
 *
 * - Call [ScreenViewHolder.startShowing] to initialize the new view with its
 *   first [Screen] rendering, taking care to include a [ViewStarter] lambda that invokes
 *   [WorkflowLifecycleOwner.installOn][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner.installOn]
 *
 * - On subsequent updates, if [ScreenViewHolder.canShow] returns true, call [ScreenViewHolder.show]
 *
 * - If [ScreenViewHolder.canShow] returns false, it is time to tear your [ScreenViewHolder]
 *   down. Be sure to call
 *   [WorkflowLifecycleOwner.destroyOnDetach][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner.destroyOnDetach]
 *   on the managed [view][ScreenViewHolder.view]!
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactory<in ScreenT : Screen> : ViewRegistry.Entry<ScreenT> {
  public val type: KClass<in ScreenT>

  override val key: Key<ScreenT, ScreenViewFactory<*>> get() = Key(type, ScreenViewFactory::class)

  /**
   * It is rare to call this method directly. Instead the most common path is to pass [Screen]
   * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] machinery for
   * you.
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
     * to show renderings of type [ScreenT] : [Screen], using the given [showRendering] lambda.
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
     * If the view doesn't need to be initialized before [ScreenViewRunner.showRendering]
     * is called, use the variant above which just takes a lambda.
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
     * Convenience treatment of [map] for use with [Wrapper] renderings with
     * the least possible boilerplate.
     *
     * For example, to make a [Wrapper] that customizes [View] initialization:
     *
     *    class WithTutorialTips<W : Screen>(
     *      override val content: W
     *    ) : AndroidScreen<WithTutorialTips<W>>, Wrapper<Screen, W> {
     *      override val viewFactory = forWrapper<WithTutorialTips<W>, W>(
     *        beforeShowing = { newHolder -> TutorialTipRunner.initialize(it.view) }
     *      )
     *
     *      override fun <U : Screen> map(transform: (W) -> U) =
     *        WithTutorialTips(transform(content))
     *    }
     */
    @WorkflowUiExperimentalApi
    public inline fun <reified WrapperT, ContentT : Screen> forWrapper(
      crossinline prepEnvironment: (environment: ViewEnvironment) -> ViewEnvironment = { it },
      crossinline prepContext: (
        environment: ViewEnvironment,
        context: Context
      ) -> Context = { _, c -> c },
      crossinline beforeShowing: (viewHolder: ScreenViewHolder<WrapperT>) -> Unit = {},
      crossinline showWrapper: (
        view: View,
        wrapper: WrapperT,
        environment: ViewEnvironment,
        showContent: (ContentT, ViewEnvironment) -> Unit
      ) -> Unit = { _, src, e, show -> show(src.content, e) },
    ): ScreenViewFactory<WrapperT>
      where WrapperT : Screen, WrapperT : Wrapper<Screen, ContentT> = map(
      prepEnvironment = prepEnvironment,
      prepContext = prepContext,
      beforeShowing = beforeShowing,
      showSource = { view, wrapper, _, environment, showContent ->
        showWrapper(view, wrapper, environment, showContent)
      },
      transform = { it.content }
    )

    /**
     * Creates a [ScreenViewFactory] for renderings of type [SourceT], which finds and
     * delegates to the factory for [TransformedT]. Allows [SourceT] to add information
     * or behavior, without requiring wasteful parallel wrapping in the view system.
     *
     * See [forWrapper] if [SourceT] implements [Wrapper].
     *
     * For example, to display a [Screen] with a strictly locked down layering policy
     * via the built-in type
     * [BodyAndOverlaysScreen][com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen]:
     *
     *    class Layers(
     *      val base: Screen,
     *      val wizard: BackStackScreen<*>? = null,
     *      val alert: AlertOverlay? = null
     *    ) : AndroidScreen<Layers> {
     *      override val viewFactory : ScreenViewFactory<Layers> = map { newLayers ->
     *        BodyAndOverlaysScreen(
     *          newLayers.base, listOfNotNull(newLayers.wizard, newLayers.alert)
     *        )
     *      }
     *    }
     *
     * @param prepEnvironment optional function used to process the initial [ViewEnvironment]
     * before the [ScreenViewFactory] is fetched, and again before the [View] is built.
     * **Note that this function is not applied on updates.** Add a [showSource] function
     * if you need that.
     *
     * @param prepContext optional function to process the [Context] used to create
     * a [View]. it is passed the product of [prepEnvironment]
     *
     * @param beforeShowing optional function to be invoked immediately after a new [View] is built.
     *
     * @param showSource function to be invoked when an instance of [SourceT] needs
     * to be shown in a [View] built to display instances of [TransformedT]. Allows pre-
     * and post-processing of the [View]. Default implementation simply applies [transform].
     *
     * @param transform function to derive a [TransformedT] from a [SourceT].
     */
    @WorkflowUiExperimentalApi
    public inline fun <reified SourceT : Screen, TransformedT : Screen> map(
      crossinline prepEnvironment: (environment: ViewEnvironment) -> ViewEnvironment = { it },
      crossinline prepContext: (
        environment: ViewEnvironment,
        context: Context
      ) -> Context = { _, c -> c },
      crossinline beforeShowing: (viewHolder: ScreenViewHolder<SourceT>) -> Unit = {},
      crossinline showSource: (
        view: View,
        source: SourceT,
        transformer: (source: SourceT) -> TransformedT,
        environment: ViewEnvironment,
        showTransformed: (TransformedT, ViewEnvironment) -> Unit
      ) -> Unit = { _, src, xform, e, show -> show(xform(src), e) },
      noinline transform: (source: SourceT) -> TransformedT,
    ): ScreenViewFactory<SourceT> {
      return fromCode { initialRendering, initialEnvironment, context, container ->
        // We take care to apply prepEnvironment before searching for the factory
        // that can handle TransformedT.
        val preppedEnvironment = prepEnvironment(initialEnvironment)
        // The "real" factory, which can accept TransformedT.
        val innerFactory = transform(initialRendering).toViewFactory(preppedEnvironment)
        // The derived factory, which can accept SourceT.
        val outerFactory = innerFactory.map(transform, prepEnvironment, prepContext, showSource)

        // Note that we give the derived factory the original initialEnvironment.
        // It applies prepEnvironment itself.
        outerFactory.buildView(initialRendering, initialEnvironment, context, container)
          .also { newViewHolder -> beforeShowing(newViewHolder) }
      }
    }
  }
}

/**
 * It is rare to call this method directly. Instead the most common path is to pass [Screen]
 * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory],
 * [ScreenViewHolder], and [ScreenViewFactoryFinder] machinery for you.
 *
 * See [ScreenViewFactory] for details.
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.toViewFactory(
  environment: ViewEnvironment
): ScreenViewFactory<ScreenT> {
  return environment[ScreenViewFactoryFinder].requireViewFactoryForRendering(environment, this)
}

/**
 * It is rare to call this method directly. Instead the most common path is to pass [Screen]
 * instances to [WorkflowViewStub.show], which will apply the [ScreenViewFactory] and
 * [ScreenViewHolder] machinery for you. See [ScreenViewFactory] for details.
 *
 * Initializes a [ScreenViewHolder] that has just been created by [ScreenViewFactory.buildView].
 *
 * To add more initialization behavior (typically a call to
 * [WorkflowLifecycleOwner.installOn][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner.installOn]),
 * provide a [viewStarter].
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewHolder<ScreenT>.startShowing(
  initialRendering: ScreenT,
  initialEnvironment: ViewEnvironment,
  viewStarter: ViewStarter?
) {
  val resolvedStarter = viewStarter ?: ViewStarter { _, doStart -> doStart() }

  var shown = false
  resolvedStarter.startView(view) {
    show(initialRendering, initialEnvironment)
    shown = true
  }
  check(shown) {
    "$viewStarter neglected to call the given doStart() function when showing $initialRendering"
  }
}

/**
 * A wrapper for the function invoked when [ScreenViewHolder.startShowing] is called, allowing
 * for custom initialization of a newly built [View] before or after the first call to
 * [ScreenViewHolder.show]. Most typical use is to call
 * [WorkflowLifecycleOwner.installOn][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner.installOn]),
 * at just the right moment.
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
 * Convenience that combines [ScreenViewFactory.buildView] and [ScreenViewHolder.startShowing],
 * since we rarely need to do work between those two calls.
 */
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
  ).apply {
    startShowing(initialRendering, initialEnvironment, viewStarter)
  }
}

/**
 * Transforms a [ScreenViewFactory] of [TransformedT] into one that can handle
 * instances of [SourceT].
 *
 * @see [ScreenViewFactory.map].
 */
@WorkflowUiExperimentalApi
public inline fun <reified SourceT : Screen, TransformedT : Screen> ScreenViewFactory<TransformedT>.map(
  noinline transform: (wrapperScreen: SourceT) -> TransformedT,
  crossinline prepEnvironment: (environment: ViewEnvironment) -> ViewEnvironment = { e -> e },
  crossinline prepContext: (
    environment: ViewEnvironment,
    context: Context
  ) -> Context = { _, c -> c },
  crossinline showSource: (
    view: View,
    source: SourceT,
    transform: (wrapperScreen: SourceT) -> TransformedT,
    environment: ViewEnvironment,
    showTransformed: (TransformedT, ViewEnvironment) -> Unit
  ) -> Unit = { _, src, xform, e, showTransformed -> showTransformed(xform(src), e) }
): ScreenViewFactory<SourceT> {
  val wrappedFactory = this

  return object : ScreenViewFactory<SourceT> by fromCode(
    buildView = { initialRendering, initialEnvironment, context, container ->
      val preppedInitialEnvironment = prepEnvironment(initialEnvironment)
      val preppedContext = prepContext(preppedInitialEnvironment, context)

      val wrappedHolder = wrappedFactory.buildView(
        transform(initialRendering),
        preppedInitialEnvironment,
        preppedContext,
        container
      )

      object : ScreenViewHolder<SourceT> {
        override val view = wrappedHolder.view
        override val environment: ViewEnvironment get() = wrappedHolder.environment

        override val runner: ScreenViewRunner<SourceT> =
          ScreenViewRunner { newSource, newEnvironment ->
            showSource(view, newSource, transform, newEnvironment) { transformed, env ->
              wrappedHolder.runner.showRendering(transformed, env)
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
