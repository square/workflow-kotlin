@file:Suppress("FunctionName")

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

/**
 * [ViewFactory]s that are always available.
 */
@WorkflowUiExperimentalApi
internal val defaultViewFactories = ViewRegistry(NamedViewFactory)

/**
 * A collection of [ViewFactory]s that can be used to display the stream of renderings
 * from a workflow tree.
 *
 * Two concrete [ViewFactory] implementations are provided:
 *
 *  - The various [bind][LayoutRunner.bind] methods on [LayoutRunner] allow easy use of
 *    Android XML layout resources and [AndroidX ViewBinding][androidx.viewbinding.ViewBinding].
 *
 *  - [BuilderViewFactory] allows views to be built from code.
 *
 *  Registries can be assembled via concatenation, making it easy to snap together screen sets.
 *  For example:
 *
 *     val AuthViewFactories = ViewRegistry(
 *         AuthorizingLayoutRunner, LoginLayoutRunner, SecondFactorLayoutRunner
 *     )
 *
 *     val TicTacToeViewFactories = ViewRegistry(
 *         NewGameLayoutRunner, GamePlayLayoutRunner, GameOverLayoutRunner
 *     )
 *
 *     val ApplicationViewFactories = ViewRegistry(ApplicationLayoutRunner) +
 *         AuthViewFactories + TicTacToeViewFactories
 *
 * In the above example, note that the `companion object`s of the various [LayoutRunner] classes
 * honor a convention of implementing [ViewFactory], in aid of this kind of assembly.
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
   * Returns the [ViewFactory] that was registered for the given [renderingType].
   *
   * @throws IllegalArgumentException if no factory can be found for type [RenderingT]
   */
  public fun <RenderingT : Any> getFactoryFor(
    renderingType: KClass<out RenderingT>
  ): ViewFactory<RenderingT>

  public companion object : ViewEnvironmentKey<ViewRegistry>(ViewRegistry::class) {
    override val default: ViewRegistry
      get() = error("There should always be a ViewRegistry hint, this is bug in Workflow.")
  }
}

@WorkflowUiExperimentalApi
public fun ViewRegistry(vararg bindings: ViewFactory<*>): ViewRegistry = TypedViewRegistry(*bindings)

/**
 * Returns a [ViewRegistry] that merges all the given [registries].
 */
@WorkflowUiExperimentalApi
public fun ViewRegistry(vararg registries: ViewRegistry): ViewRegistry = CompositeViewRegistry(*registries)

/**
 * Returns a [ViewRegistry] that contains no bindings.
 *
 * Exists as a separate overload from the other two functions to disambiguate between them.
 */
@WorkflowUiExperimentalApi
public fun ViewRegistry(): ViewRegistry = TypedViewRegistry()

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * Creates a [View] to display [initialRendering], which can be updated via calls
 * to [View.showRendering].
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
  container: ViewGroup? = null
): View {
  return getFactoryFor(initialRendering::class)
      .buildView(
          initialRendering,
          initialViewEnvironment,
          contextForNewView,
          container
      )
      .apply {
        check(this.getRendering<Any>() != null) {
          "View.bindShowRendering should have been called for $this, typically by the " +
              "${ViewFactory::class.java.name} that created it."
        }
      }
}

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * Creates a [View] to display [initialRendering], which can be updated via calls
 * to [View.showRendering].
 *
 * @throws IllegalArgumentException if no binding can be find for type [RenderingT]
 *
 * @throws IllegalStateException if the matching [ViewFactory] fails to call
 * [View.bindShowRendering] when constructing the view
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewRegistry.buildView(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  container: ViewGroup
): View = buildView(initialRendering, initialViewEnvironment, container.context, container)

@WorkflowUiExperimentalApi
public operator fun ViewRegistry.plus(binding: ViewFactory<*>): ViewRegistry =
  this + ViewRegistry(binding)

@WorkflowUiExperimentalApi
public operator fun ViewRegistry.plus(other: ViewRegistry): ViewRegistry = ViewRegistry(this, other)
