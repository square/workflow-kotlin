package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
public interface VisualHolder<in RenderingT, out VisualT> {
  public val visual: VisualT

  public fun update(rendering: RenderingT): Boolean

  public companion object {
    public operator fun <RenderingT, VisualT> invoke(
      visual: VisualT,
      onUpdate: (RenderingT) -> Unit
    ): VisualHolder<RenderingT, VisualT> {
      return object : VisualHolder<RenderingT, VisualT> {
        override val visual = visual

        override fun update(rendering: RenderingT): Boolean {
          onUpdate(rendering)
          return true
        }
      }
    }
  }
}
