package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
public class AsScreen<W : Any>(
  private val rendering: W
) : AndroidScreen<AsScreen<*>>, Compatible {
  init {
      check(rendering !is Screen) {
        "AsScreen is for converting non-Screen renderings, it should not wrap Screen $rendering."
      }
  }

  override val compatibilityKey: String
    get() = Compatible.keyFor(rendering)

  override val viewFactory: ScreenViewFactory<AsScreen<*>> = ManualScreenViewFactory(
    type = AsScreen::class,
    viewConstructor = { initialRendering, initialViewEnvironment, context, container ->
      @Suppress("DEPRECATION")
      initialViewEnvironment[ViewRegistry]
        .getFactoryForRendering(initialRendering)
        .buildView(initialRendering, initialViewEnvironment, context, container)
    }
  )

  public companion object {
    /**
     * Transforms [rendering] to implement [Screen], wrapping it in an [AsScreen] if necessary.
     */
    public fun asScreen(rendering: Any): Screen {
      return when(rendering) {
        is Screen -> rendering
        else -> AsScreen(rendering)
      }
    }
  }
}
