package com.squareup.workflow1.ui

/**
 * A rendering that wraps another, to which it delegates for UI construction duties.
 */
@WorkflowUiExperimentalApi
public interface Wrapper<W : Any> : Compatible {
  public val wrapped: W

  override val compatibilityKey: String
    get() = Compatible.keyFor(unwrap())
}

@WorkflowUiExperimentalApi
public tailrec fun Wrapper<*>.unwrap(): Any {
  (wrapped as? Wrapper<*>)?.let { return it.unwrap() }
  return wrapped
}

@WorkflowUiExperimentalApi
public fun unwrap(rendering: Any): Any {
  return if (rendering is Wrapper<*>) rendering.unwrap() else rendering
}

@WorkflowUiExperimentalApi
public inline fun <reified T> unwrapOrNull(rendering: Any): T? {
  return unwrap(rendering) as? T
}
