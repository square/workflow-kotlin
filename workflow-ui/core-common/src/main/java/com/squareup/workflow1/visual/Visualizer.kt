package com.squareup.workflow1.visual

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compatible

/**
 * Support class for containers (stubs) that can show a series of renderings that
 * might be of different types or [incompatible][compatible], and so require different
 * factories and holders.
 *
 * Provides the hooks needed for lifecycle management, including
 * view state concerns, so that creation of a [VisualHolder] is decoupled from the
 * first call to its [VisualHolder.update] function. This is achieved via the
 * `onNewVisual` lambda that callers must provide to [show]. That lambda
 * is passed another, `doFirstUpdate`, which must be called immediately (enforced
 * at runtime). The default value for `onNewVisual` does nothing else, and may be
 * all that is needed for Compose. If not, we should drop the default and force
 * callers to be thoughtful.
 *
 * Still working out exact per-app logistics, but expect to wind up with one
 * instance of this for each [VisualT] value in a system:
 *
 * - Android View
 * - Android Dialog
 * - @Composable fun () -> Unit
 *
 * And maybe a fourth for mapping Overlay to Compose's Dialog()? Or perhaps the third
 * flavor can cover that case too. Perhaps we'll decide that we always use classic
 * APIs for that.
 */
@WorkflowUiExperimentalApi
public class Visualizer<ContextT, VisualT>(
  private val loadIntegrationFactory: (VisualEnvironment) -> VisualFactory<ContextT, Any, VisualT>
) {
  private var rendering: Any? = null
  private var holder: VisualHolder<Any, VisualT>? = null

  public val visual: VisualT
    get() = requireNotNull(holder?.visual) {
      "Expected visual of $this to be non-null, has show() been called yet?"
    }

  public val visualOrNull: VisualT? get() = holder?.visual

  /**
   * Updates the receiver to display a new [rendering], using [context] and [environment]
   * to create a new [VisualT] if necessary. If a new [VisualT] is created, the optional
   * [onNewVisual] will be called. [onNewVisual] must call the provided `firstUpdate`
   * function it is passed.
   *
   * TODO: Does [onNewVisual] need access to the [VisualEnvironment] too?
   */
  public fun show(
    rendering: Any,
    context: ContextT,
    environment: VisualEnvironment,
    onNewVisual: (visual: VisualT, doFirstUpdate: () -> Unit) -> Unit = { _, u -> u() }
  ) {
    val compatible = this.rendering?.let { compatible(it, rendering) } == true

    if (!compatible || (holder?.update(rendering) != true)) {
      this.rendering = rendering

      holder = getFactory(environment).create(rendering, context, environment, ::getFactory)
        .also { holder ->
          var updated = false
          onNewVisual(holder.visual) {
            holder.update(rendering)
            updated = true
          }
          check(updated) {
            "The onNewVisual function passed to $this.show() must call " +
              "the given firstUpdate() function."
          }
        }
    }
  }

  private fun getFactory(
    environment: VisualEnvironment
  ): VisualFactory<ContextT, Any, VisualT> {
    return SequentialVisualFactory(listOf(loadIntegrationFactory(environment), standardFactories()))
  }
}

@WorkflowUiExperimentalApi
public fun <C, V> standardFactories(): VisualFactory<C, Any, V> {
  return SequentialVisualFactory(
    listOf(
      WithNameVisualFactory<C, Any, V>().widen(), WithEnvironmentVisualFactory<C, Any, V>().widen()
    )
  )
}
