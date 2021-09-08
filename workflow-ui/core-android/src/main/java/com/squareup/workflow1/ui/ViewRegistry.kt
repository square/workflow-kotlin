@file:Suppress("FunctionName")

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

/**
 * The [ViewEnvironment] service that can be used to display the stream of renderings
 * from a workflow tree as [View] instances. This is the engine behind [AndroidViewRendering],
 * [WorkflowViewStub] and [ViewFactory]. Most apps can ignore [ViewRegistry] as an implementation
 * detail, by using [AndroidViewRendering] to tie their rendering classes to view code.
 *
 * To avoid that coupling between workflow code and the Android runtime, registries can
 * be loaded with [ViewFactory] instances at runtime, and provided as an optional parameter to
 * [WorkflowLayout.start].
 *
 * For example:
 *
 *     val AuthViewFactories = ViewRegistry(
 *       AuthorizingLayoutRunner, LoginLayoutRunner, SecondFactorLayoutRunner
 *     )
 *
 *     val TicTacToeViewFactories = ViewRegistry(
 *       NewGameLayoutRunner, GamePlayLayoutRunner, GameOverLayoutRunner
 *     )
 *
 *     val ApplicationViewFactories = ViewRegistry(ApplicationLayoutRunner) +
 *       AuthViewFactories + TicTacToeViewFactories
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *       super.onCreate(savedInstanceState)
 *
 *       val model: MyViewModel by viewModels()
 *       setContentView(
 *         WorkflowLayout(this).apply { start(model.renderings, ApplicationViewFactories) }
 *       )
 *     }
 *
 *     /** As always, use an androidx ViewModel for state that survives config change. */
 *     class MyViewModel(savedState: SavedStateHandle) : ViewModel() {
 *       val renderings: StateFlow<Any> by lazy {
 *         renderWorkflowIn(
 *           workflow = rootWorkflow,
 *           scope = viewModelScope,
 *           savedStateHandle = savedState
 *         )
 *       }
 *     }
 *
 * In the above example, it is assumed that the `companion object`s of the various
 * decoupled [LayoutRunner] classes honor a convention of implementing [ViewFactory], in
 * aid of this kind of assembly.
 *
 *     class GamePlayLayoutRunner(view: View) : LayoutRunner<GameRendering> {
 *
 *       // ...
 *
 *       companion object : ViewFactory<GameRendering> by LayoutRunner.bind(
 *         R.layout.game_layout, ::GameLayoutRunner
 *       )
 *     }
 */
@WorkflowUiExperimentalApi
public interface ViewRegistry {
  /**
   * The set of unique keys which this registry can derive from the renderings passed to [buildView]
   * and for which it knows how to create views.
   *
   * Used to ensure that duplicate bindings are never registered.
   */
  public val keys: Set<KClass<*>>

  /**
   * This method is not for general use, use [WorkflowViewStub] instead.
   *
   * Returns the [ViewFactory] that was registered for the given [renderingType], or null
   * if none was found.
   */
  public fun <RenderingT : Any> getFactoryFor(
    renderingType: KClass<out RenderingT>
  ): ViewFactory<RenderingT>?

  public companion object : ViewEnvironmentKey<ViewRegistry>(ViewRegistry::class) {
    override val default: ViewRegistry get() = ViewRegistry()
  }
}

@WorkflowUiExperimentalApi
public fun ViewRegistry(vararg bindings: ViewFactory<*>): ViewRegistry =
  TypedViewRegistry(*bindings)

/**
 * Returns a [ViewRegistry] that contains no bindings.
 *
 * Exists as a separate overload from the other two functions to disambiguate between them.
 */
@WorkflowUiExperimentalApi
public fun ViewRegistry(): ViewRegistry = TypedViewRegistry()

/**
 * It is usually more convenient to use [WorkflowViewStub] or [DecorativeViewFactory]
 * than to call this method directly.
 *
 * Returns the [ViewFactory] that builds [View] instances suitable to display the given [rendering],
 * via subsequent calls to [View.showRendering].
 *
 * Prefers factories found via [ViewRegistry.getFactoryFor]. If that returns null, falls
 * back to the factory provided by the rendering's implementation of
 * [AndroidViewRendering.viewFactory], if there is one. Note that this means that a
 * compile time [AndroidViewRendering.viewFactory] binding can be overridden at runtime.
 *
 * The returned view will have a
 * [WorkflowLifecycleOwner][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner]
 * set on it. The returned view must EITHER:
 *
 * 1. Be attached at least once to ensure that the lifecycle eventually gets destroyed (because its
 *    parent is destroyed), or
 * 2. Have its
 *    [WorkflowLifecycleOwner.destroyOnDetach][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner.destroyOnDetach]
 *    called, which will either schedule the
 *    lifecycle to be destroyed if the view is attached, or destroy it immediately if it's detached.
 *
 * @throws IllegalArgumentException if no factory can be find for type [RenderingT]
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any>
  ViewRegistry.getFactoryForRendering(rendering: RenderingT): ViewFactory<RenderingT> {
  val unwrapped = unwrap(rendering)
  @Suppress("UNCHECKED_CAST")
  return getFactoryFor(unwrapped::class)
    ?: (unwrapped as? AndroidViewRendering<*>)?.viewFactory as? ViewFactory<RenderingT>
    ?: throw IllegalArgumentException(
      "A ${ViewFactory::class.qualifiedName} should have been registered to display " +
        "${rendering::class.qualifiedName} instances, or that class should implement " +
        "${AndroidViewRendering::class.simpleName}<${rendering::class.simpleName}>."
    )
}

/**
 * It is usually more convenient to use [WorkflowViewStub] or [DecorativeViewFactory]
 * than to call this method directly.
 *
 * Finds a [ViewFactory] to create a [View] to display [initialRendering]. The new view
 * can be updated via calls to [View.showRendering] -- that is, it is guaranteed that
 * [bindShowRendering] has been called on this view.
 *
 * The returned view will have a
 * [WorkflowLifecycleOwner][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner]
 * set on it. The returned view must EITHER:
 *
 * 1. Be attached at least once to ensure that the lifecycle eventually gets destroyed (because its
 *    parent is destroyed), or
 * 2. Have its
 *    [WorkflowLifecycleOwner.destroyOnDetach][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner.destroyOnDetach]
 *    called, which will either schedule the
 *    lifecycle to be destroyed if the view is attached, or destroy it immediately if it's detached.
 *
 * @param initializeView Optional function invoked immediately after the [View] is
 * created (that is, immediately after the call to [ViewFactory.buildView]).
 * [showRendering], [getRendering] and [environment] are all available when this is called.
 * Defaults to a call to [View.showFirstRendering].
 *
 * @throws IllegalArgumentException if no factory can be find for type [RenderingT]
 *
 * @throws IllegalStateException if the matching [ViewFactory] fails to call
 * [View.bindShowRendering] when constructing the view
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewRegistry.buildView(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  initializeView: View.() -> Unit = { showFirstRendering() }
): View {
  return getFactoryForRendering(initialRendering).buildView(
    initialRendering, initialViewEnvironment, contextForNewView, container
  ).also { view ->
    checkNotNull(view.showRenderingTag) {
      "View.bindShowRendering should have been called for $view, typically by the " +
        "${ViewFactory::class.java.name} that created it."
    }
    initializeView.invoke(view)
  }
}

@WorkflowUiExperimentalApi
public operator fun ViewRegistry.plus(binding: ViewFactory<*>): ViewRegistry =
  this + ViewRegistry(binding)

@WorkflowUiExperimentalApi
public operator fun ViewRegistry.plus(other: ViewRegistry): ViewRegistry =
  CompositeViewRegistry(this, other)

/**
 * Default implementation for the `initializeView` argument of [ViewRegistry.buildView],
 * and for [DecorativeViewFactory.initializeView]. Calls [showRendering] against
 * [getRendering] and [environment].
 */
@WorkflowUiExperimentalApi
public fun View.showFirstRendering() {
  showRendering(getRendering()!!, environment!!)
}
