package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * Returns a decorated factory of a wider rendering type, that is, it accepts a more general
 * rendering type. This convenience has four generic types so it's convenient to leverage
 * type-inference. For instance:
 *
 * ```
 * AggregatedFactory<I, Any, O> {
 *   val factories: List<VisualFactory<I, Any, O>
 *   inline fun <reified SubType> add(factory: VisualFactory<I, SubType, O> {
 *     factories += factory.widen()
 *   }
 * }
 * ```
 *
 * @see WidenedTypeVisualFactory
 */
@WorkflowUiExperimentalApi
public inline fun <ContextT, reified WidenT : Any, reified NarrowT : WidenT, VisualT>
  VisualFactory<ContextT, NarrowT, VisualT>.widen(): VisualFactory<ContextT, WidenT, VisualT> =
  // If you are not really widening save a wrapper factory.
  if (WidenT::class == NarrowT::class) {
    @Suppress("UNCHECKED_CAST")
    this as VisualFactory<ContextT, WidenT, VisualT>
  } else {
    WidenedTypeVisualFactory(this)
  }

/**
 * Wraps a [VisualFactory] of a narrow rendering type, and offers it as a wider type. This is used
 * inside factory aggregations: the aggregator factory takes care of selecting which sub-factory
 * to ask, and this wrapper takes care of the type-check and casting.
 *
 * @param WidenT The widen rendering type.
 * @param NarrowT The original wrapped factory rendering type.
 */
@WorkflowUiExperimentalApi
public class WidenedTypeVisualFactory<ContextT, WidenT : Any, NarrowT : WidenT, VisualT>
@PublishedApi internal constructor(
  private val narrowType: KClass<out NarrowT>,
  private val narrowFactory: VisualFactory<ContextT, NarrowT, VisualT>
) : VisualFactory<ContextT, WidenT, VisualT> {

  public companion object {
    public inline operator fun <ContextT, WidenT : Any, reified NarrowT : WidenT, VisualT> invoke(
      factory: VisualFactory<ContextT, NarrowT, VisualT>
    ): WidenedTypeVisualFactory<ContextT, WidenT, NarrowT, VisualT> = WidenedTypeVisualFactory(
      narrowType = NarrowT::class,
      narrowFactory = factory
    )
  }

  override fun createOrNull(
    rendering: WidenT,
    context: ContextT,
    environment: VisualEnvironment
  ): VisualHolder<WidenT, VisualT>? {
    return narrowType.safeCast(rendering)?.let {
      narrowFactory.createOrNull(
        rendering = it,
        context = context,
        environment = environment
      )
    }?.let { narrowHolder ->
      object : VisualHolder<WidenT, VisualT> {
        override val visual: VisualT get() = narrowHolder.visual

        override fun update(rendering: WidenT): Boolean {
          return narrowType.safeCast(rendering)?.let(narrowHolder::update) == true
        }
      }
    }
  }
}
