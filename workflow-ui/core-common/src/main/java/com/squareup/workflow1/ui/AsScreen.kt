@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui

/**
 * Provides backward compatibility for legacy non-[Screen] renderings based on
 * `ViewFactory` and `AndroidViewRendering`. Should be used only as a stop gap
 * until the wrapped [rendering] can be updated to implement [Screen].
 */
@Deprecated("Implement Screen directly.")
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
    get() = Compatible.keyFor(rendering)
}

/**
 * Ensures [rendering] implements [Screen], wrapping it in an [AsScreen] if necessary.
 */
@Deprecated("Implement Screen directly.")
@WorkflowUiExperimentalApi
public fun asScreen(rendering: Any): Screen {
  return when (rendering) {
    is Screen -> rendering
    else -> AsScreen(rendering)
  }
}
