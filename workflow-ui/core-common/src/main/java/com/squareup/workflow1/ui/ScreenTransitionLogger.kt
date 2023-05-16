package com.squareup.workflow1.ui

/**
 * [ViewEnvironment][com.squareup.workflow1.ui.ViewEnvironment] helper invoked by
 * standard placeholders like `WorkflowViewStub` and `@Composable fun WorkflowRendering`
 * as they transition between [incompatible][Compatible] [Screen]s.
 */
@WorkflowUiExperimentalApi
public fun interface ScreenTransitionLogger {
  /**
   * Invoked by the placeholder whenever a new view object is created and shown.
   * Parameters are weakly typed to allow [Screen],
   * [Overlay][com.squareup.workflow1.ui.container.Overlay], or any custom
   * rendering types to be logged.
   *
   * @param fromOrNull the rendering that was displayed previously, or null if there
   * wasn't one
   *
   * @param to the rendering whose view is being shown for the first time
   */
  public fun onTransition(
    fromOrNull: Screen?,
    to: Screen,
    environment: ViewEnvironment
  )

  public companion object : ViewEnvironmentKey<ScreenTransitionLogger>() {
    override val default: ScreenTransitionLogger = ScreenTransitionLogger { _, _, _ -> }
  }
}
