@file:Suppress("FunctionName")

package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ViewRegistry.Entry
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
  public interface Entry<in RenderingT : Any> {
    public val type: KClass<in RenderingT>
  }

  /**
   * The set of unique keys which this registry can derive from the renderings passed to
   * [getEntryFor] and for which it knows how to create views.
   *
   * Used to ensure that duplicate bindings are never registered.
   */
  public val keys: Set<KClass<*>>

  /**
   * Returns the [Entry] that was registered for the given [renderingType], or null
   * if none was found.
   */
  public fun <RenderingT : Any> getEntryFor(
    renderingType: KClass<out RenderingT>
  ): Entry<RenderingT>?

  public companion object : ViewEnvironmentKey<ViewRegistry>(ViewRegistry::class) {
    override val default: ViewRegistry get() = ViewRegistry()
  }
}

@WorkflowUiExperimentalApi public inline operator fun <reified RenderingT : Any> ViewRegistry.get(
  renderingType: KClass<out RenderingT>
): Entry<RenderingT>? = getEntryFor(renderingType)

@WorkflowUiExperimentalApi
public fun ViewRegistry(vararg bindings: Entry<*>): ViewRegistry =
  TypedViewRegistry(*bindings)

/**
 * Returns a [ViewRegistry] that contains no bindings.
 *
 * Exists as a separate overload from the other two functions to disambiguate between them.
 */
@WorkflowUiExperimentalApi
public fun ViewRegistry(): ViewRegistry = TypedViewRegistry()

/**
 *  @throws IllegalArgumentException if the receiver already has a matching [entry].
 */
@WorkflowUiExperimentalApi
public operator fun ViewRegistry.plus(entry: Entry<*>): ViewRegistry =
  this + ViewRegistry(entry)

/** @throws IllegalArgumentException if other has redundant entries. */
@WorkflowUiExperimentalApi
public operator fun ViewRegistry.plus(other: ViewRegistry): ViewRegistry =
  CompositeViewRegistry(this, other)

/**
 * Replaces the existing [ViewRegistry] of the receiver with [registry]. Use
 * [ViewEnvironment.merge] to combine them instead.
 */
@WorkflowUiExperimentalApi
public operator fun ViewEnvironment.plus(registry: ViewRegistry): ViewEnvironment {
  return this + (ViewRegistry to registry)
}

/**
 * Combines the receiver with [other]. If there are conflicting entries,
 * those in [other] are preferred.
 */
@WorkflowUiExperimentalApi
public infix fun ViewRegistry.merge(other: ViewRegistry): ViewRegistry {
  return (keys + other.keys).asSequence()
    .map { other.getEntryFor(it) ?: getEntryFor(it)!! }
    .toList()
    .toTypedArray()
    .let { ViewRegistry(*it) }
}

/**
 * Merges the [ViewRegistry] of the receiver with [registry]. If there are conflicting entries,
 * those in [registry] are preferred.
 */
@WorkflowUiExperimentalApi
public infix fun ViewEnvironment.merge(registry: ViewRegistry): ViewEnvironment {
  val oldReg = this[ViewRegistry]

  val union = (oldReg.keys + registry.keys).asSequence()
    .map { registry.getEntryFor(it) ?: oldReg.getEntryFor(it)!! }
    .toList()
    .toTypedArray()

  val unionRegistry = ViewRegistry(*union)
  return this + (ViewRegistry to unionRegistry)
}

/**
 * Combines the receiving [ViewEnvironment] with [other], taking care to merge
 * their [ViewRegistry] entries. Any other conflicting values in [other] replace those
 * in the receiver.
 */
@WorkflowUiExperimentalApi
public infix fun ViewEnvironment.merge(other: ViewEnvironment): ViewEnvironment {
  if (other.map.isEmpty()) return this

  val oldReg = this[ViewRegistry]
  val newReg = other[ViewRegistry]
  return this + other + (ViewRegistry to oldReg.merge(newReg))
}
