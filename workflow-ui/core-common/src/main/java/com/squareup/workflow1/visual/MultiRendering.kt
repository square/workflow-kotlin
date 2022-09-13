package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Base class for containers (stubs) that can show a series of renderings that might be of
 * different types or compatibilities, and so require different factories and holders.
 */
@WorkflowUiExperimentalApi
public abstract class MultiRendering<UiContextT, VisualT> {

  private var currentHolder: VisualHolder<Any, VisualT>? = null

  public val currentVisual: VisualT get() = currentHolder!!.visual

  public val currentVisualOrNull: VisualT? get() = currentHolder?.visual

  /**
   * Updates the receiver to display a new [rendering], using [context] and [environment]
   * to create a new [VisualT] if necessary. If a new [VisualT] is created, the optional
   * [onNewVisual] will be called. [onNewVisual] must call the provided `firstUpdate`
   * function it is passed.
   */
  public fun <RenderingT : Any> replaceWith(
    rendering: RenderingT,
    context: UiContextT,
    environment: VisualEnvironment,
    onNewVisual: (visual: VisualT, doFirstUpdate: () -> Unit) -> Unit = { _, u -> u() }
  ) {
    if (currentHolder?.update(rendering) != true) {
      currentHolder = create(rendering, context, environment).also { holder ->
        var updated = false
        onNewVisual(holder.visual) {
          holder.update(rendering)
          updated = true
        }
        check(updated) {
          "The onNewVisual function passed to $this.replaceWith() must call " +
            "the given firstUpdate() function."
        }
      }
    }
  }

  protected abstract fun create(
    rendering: Any,
    context: UiContextT,
    environment: VisualEnvironment
  ): VisualHolder<Any, VisualT>
}
