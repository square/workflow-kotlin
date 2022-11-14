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
  protected open val name: String = this::class.simpleName ?: "Wrapper"

  final override val compatibilityKey: String by lazy { Compatible.keyFor(wrapped, name) }

  override fun toString(): String {
    return "$name('${Compatible.keyFor(wrapped)}')"
  }
}
