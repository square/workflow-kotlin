package com.squareup.workflow1.ui

/**
 * Provides backward compatibility for legacy non-[Screen] renderings.
 * This is a migration tool for code bases that are still adopting the `Screen` and
 * `Overlay` interfaces, and will be deprecated and deleted sooner or later.
 */
@WorkflowUiExperimentalApi
public class AsScreen<W : Any>(
  public val rendering: W
) : Screen, Compatible {
  init {
    check(rendering !is Screen) {
      "AsScreen is for converting non-Screen renderings, it should not wrap Screen $rendering."
    }
  }

  override val compatibilityKey: String
    get() = Compatible.keyFor(rendering, "AsScreen")
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
