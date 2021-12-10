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
 * @throws IllegalArgumentException if no factory can be find for type [RenderingT]
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any>
  ViewRegistry.getFactoryForRendering(rendering: RenderingT): ViewFactory<RenderingT> {
  @Suppress("UNCHECKED_CAST")
  return getFactoryFor(rendering::class)
    ?: (rendering as? AndroidViewRendering<*>)?.viewFactory as? ViewFactory<RenderingT>
    ?: (rendering as? Named<*>)?.let { NamedViewFactory as ViewFactory<RenderingT> }
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
 * Finds a [ViewFactory] to create a [View] ready to display [initialRendering]. The caller
 * is responsible for calling [View.start] on the new [View]. After that,
 * [View.showRendering] can be used to update it with new renderings that
 * are [compatible] with [initialRendering].
 *
 * @param viewStarter An optional wrapper for the function invoked when [View.start]
 * is called, allowing for last second initialization of a newly built [View].
 * See [ViewStarter] for details.
 *
 * @throws IllegalArgumentException if no factory can be found for type [RenderingT]
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
  viewStarter: ViewStarter? = null,
): View {
  return getFactoryForRendering(initialRendering).buildView(
    initialRendering, initialViewEnvironment, contextForNewView, container
  ).also { view ->
    checkNotNull(view.workflowViewStateOrNull) {
      "View.bindShowRendering should have been called for $view, typically by the " +
        "${ViewFactory::class.java.name} that created it."
    }
    viewStarter?.let { givenStarter ->
      val doStart = view.starter
      view.starter = { newView ->
        givenStarter.startView(newView) { doStart.invoke(newView) }
      }
    }
  }
}

/**
 * A wrapper for the function invoked when [View.start] is called, allowing for
 * last second initialization of a newly built [View]. Provided via [ViewRegistry.buildView]
 * or [DecorativeViewFactory.viewStarter].
 *
 * While [View.getRendering] may be called from [startView], it is not safe to
 * assume that the type of the rendering retrieved matches the type the view was
 * originally built to display. [ViewFactories][ViewFactory] can be wrapped, and
 * renderings can be mapped to other types.
 */
@WorkflowUiExperimentalApi
public fun interface ViewStarter {
  /** Called from [View.start]. [doStart] must be invoked. */
  public fun startView(
    view: View,
    doStart: () -> Unit
  )
}

@WorkflowUiExperimentalApi
public operator fun ViewRegistry.plus(binding: ViewFactory<*>): ViewRegistry =
  this + ViewRegistry(binding)

@WorkflowUiExperimentalApi
public operator fun ViewRegistry.plus(other: ViewRegistry): ViewRegistry =
  CompositeViewRegistry(this, other)
