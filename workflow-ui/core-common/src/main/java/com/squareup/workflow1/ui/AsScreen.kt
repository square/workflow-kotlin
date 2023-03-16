package com.squareup.workflow1.ui

/**
 * Provides backward compatibility for legacy non-[Screen] renderings.
 * This is a migration tool for code bases that are still adopting the `Screen` and
 * `Overlay` interfaces, and will be deprecated and deleted sooner or later.
 */
@WorkflowUiExperimentalApi
public class AsScreen<C : Any>(
  override val content: C
) : Screen, Wrapper<Any, C> {
  init {
    check(content !is Screen) {
      "AsScreen is for converting non-Screen renderings, it should not wrap Screen $content."
    }
  }

  @Deprecated("Use content", ReplaceWith("content"))
  public val rendering: C = content

  override fun <D : Any> map(transform: (C) -> D): AsScreen<D> =
    AsScreen(transform(content))
}

/**
 * Ensures [rendering] implements [Screen], wrapping it in an [AsScreen] if necessary.
 *
 * This is a migration tool for code bases that are still adopting the `Screen` and
 * `Overlay` interfaces, and will be deprecated and deleted sooner or later.
 */
@WorkflowUiExperimentalApi
public fun asScreen(rendering: Any): Screen {
  return when (rendering) {
    is Screen -> rendering
    else -> AsScreen(rendering)
  }
}
