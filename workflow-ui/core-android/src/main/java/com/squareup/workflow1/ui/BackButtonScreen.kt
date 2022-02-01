package com.squareup.workflow1.ui

@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
@Deprecated(
  "Use com.squareup.workflow1.ui.container.BackButtonScreen",
  ReplaceWith("BackButtonScreen", "com.squareup.workflow1.ui.container.BackButtonScreen")
)
public class BackButtonScreen<W : Any>(
  public val wrapped: W,
  public val shadow: Boolean = false,
  public val onBackPressed: (() -> Unit)? = null
) : AndroidViewRendering<BackButtonScreen<*>> {
  override val viewFactory: ViewFactory<BackButtonScreen<*>> = DecorativeViewFactory(
    type = BackButtonScreen::class,
    map = { outer -> outer.wrapped },
    doShowRendering = { view, innerShowRendering, outerRendering, viewEnvironment ->
      if (!outerRendering.shadow) {
        // Place our handler before invoking innerShowRendering, so that
        // its later calls to view.backPressedHandler will take precedence
        // over ours.
        view.backPressedHandler = outerRendering.onBackPressed
      }

      innerShowRendering.invoke(outerRendering.wrapped, viewEnvironment)

      if (outerRendering.shadow) {
        // Place our handler after invoking innerShowRendering, so that ours wins.
        view.backPressedHandler = outerRendering.onBackPressed
      }
    }
  )
}
