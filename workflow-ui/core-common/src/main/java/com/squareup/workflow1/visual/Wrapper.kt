package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Base class for wrapper rendering types. Ensures that [Compatible] is implemented
 * correctly, and a more regular structure should be an aid to test code if nothing
 * else.
 */
@WorkflowUiExperimentalApi
public abstract class Wrapper<W : Any>(
  public val wrapped: W,
) : Compatible {
  /** Read only once by [compatibilityKey], at construction time. */
  protected open val name: String = this::class.qualifiedName ?: "Wrapper"

  final override val compatibilityKey: String by lazy { Compatible.keyFor(wrapped, name) }
}

/**
 * Base type for [Wrapper]s that are used to change the
 * [compatibility][com.squareup.workflow1.ui.compatible] of renderings
 * that do not implement [Compatible] themselves. The [withName] function
 * can create [VisualFactory] instances for simple subclasses.
 *
 * TODO: This should be called Named, but that's taken at the moment.
 *  Be sure to rename it after the currently deprecated classes are deleted
 *  -- and be sure to delete them before dropping the VisualFactory bomb
 */
@WorkflowUiExperimentalApi
public abstract class WithName<W : Any>(
  wrapped: W,
  name: String
) : Wrapper<W>(wrapped) {
  public override val name: String by lazy {
    require(name.isNotBlank()) { "name must not be blank." }
    name
  }
}

/**
 * Converts a [VisualFactory] for renderings of type [R] to one that can unwrap
 * all implementations of [WithName]<[R]>
 */
@WorkflowUiExperimentalApi
public fun <
  C, R : Any, W : WithName<R>, V
  > VisualFactory<C, R, V>.withName(): VisualFactory<C, W, V> {
  val delegateFactory = this

  return object : VisualFactory<C, W, V> {
    override fun createOrNull(
      rendering: W,
      context: C,
      environment: VisualEnvironment
    ): VisualHolder<W, V> {
      val delegateHolder = delegateFactory.create(rendering.wrapped, context, environment)

      return object : VisualHolder<W, V> {
        override val visual: V get() = delegateHolder.visual

        override fun update(rendering: W): Boolean {
          return delegateHolder.update(rendering.wrapped)
        }
      }
    }
  }
}
