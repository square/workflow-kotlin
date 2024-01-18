package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
public fun interface VisualFactory<ContextT, in RenderingT, VisualT> {
  /**
   * Given a ui model ([rendering]), creates a [VisualHolder] which pairs:
   *
   * - a native view system object of type [VisualT] -- a [visual][VisualHolder.visual]
   * - an [update function][VisualHolder.update] to apply [RenderingT] instances to
   *   the new [VisualT] instance.
   *
   * This method must not call [VisualHolder.update], to ensure that callers have
   * complete control over the lifecycle of the new [VisualT].
   *
   * @param getFactory can be used to make recursive calls to build VisualT
   * instances for sub-parts of [rendering]
   */
  public fun createOrNull(
    rendering: RenderingT,
    context: ContextT,
    environment: ViewEnvironment,
    getFactory: (ViewEnvironment) -> VisualFactory<ContextT, Any, VisualT>
  ): VisualHolder<RenderingT, VisualT>?
}
