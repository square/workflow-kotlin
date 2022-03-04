package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ViewRegistry.Entry

/**
 * A [ViewableRendering] that delegates its view construction to another [actual]
 * instance.
 *
 * - A [AliasRendering] can modify the wrapped rendering's  compatibility
 *   policy by customizing [compatibilityKey].
 *
 * - When the view modeled by this rendering is being built or updated, a [AliasRendering]
 *   can define the [ViewEnvironment] by customizing [unwrap].
 */
@WorkflowUiExperimentalApi
public interface AliasRendering : ViewableRendering, Compatible {
  public val actual: ViewableRendering

  override val compatibilityKey: String get() = Compatible.keyFor(actual)

  public fun unwrap(viewEnvironment: ViewEnvironment): Pair<ViewableRendering, ViewEnvironment> {
    return (actual as? AliasRendering)?.let { it.unwrap(viewEnvironment) }
      ?: Pair(actual, viewEnvironment)
  }
}

@WorkflowUiExperimentalApi
public fun unwrapRendering(
  rendering: ViewableRendering,
  viewEnvironment: ViewEnvironment
): Pair<ViewableRendering, ViewEnvironment> {
  return if (rendering is AliasRendering) rendering.unwrap(viewEnvironment)
  else Pair(rendering, viewEnvironment)
}

@WorkflowUiExperimentalApi
public fun <R: ViewableRendering> ViewEnvironment.unwrapRenderingAndGetFactory(
  rendering: R
): Triple<Any, ViewEnvironment, Entry<R>?> {
  val (resolvedRendering, resolvedEnvironment) = unwrapRendering(rendering, this)
  return resolvedEnvironment[ViewRegistry].getEntryFor(resolvedRendering::class).let { factory ->
    Triple(resolvedRendering, resolvedEnvironment, factory)
  }
}
