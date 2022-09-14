package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compatible

/**
 * Base support class for containers (stubs) that can show a series of renderings that
 * might be of different types or [incompatible][compatible], and so require different
 * factories and holders.
 */
@WorkflowUiExperimentalApi
public abstract class MultiRendering<UiContextT, VisualT> {
  private var rendering: Any? = null
  private var holder: VisualHolder<Any, VisualT>? = null

  public val visual: VisualT get() = requireNotNull(holder?.visual) {
    "visual of $this is null, probably replaceWith hasn't been called yet."
  }

  public val visualOrNull: VisualT? get() = holder?.visual

  /**
   * Updates the receiver to display a new [rendering], using [context] and [environment]
   * to create a new [VisualT] if necessary. If a new [VisualT] is created, the optional
   * [onNewVisual] will be called. [onNewVisual] must call the provided `firstUpdate`
   * function it is passed.
   */
  public fun replaceWith(
    rendering: Any,
    context: UiContextT,
    environment: VisualEnvironment,
    onNewVisual: (visual: VisualT, doFirstUpdate: () -> Unit) -> Unit = { _, u -> u() }
  ) {
    val compatible = this.rendering?.let { compatible(it, rendering) } == true

    if (!compatible || (holder?.update(rendering) != true)) {
      this.rendering = rendering

      holder = create(rendering, context, environment).also { holder ->
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
