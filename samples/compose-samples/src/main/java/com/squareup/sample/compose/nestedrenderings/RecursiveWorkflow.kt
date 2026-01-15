package com.squareup.sample.compose.nestedrenderings

import androidx.compose.ui.util.fastForEach
import com.squareup.sample.compose.databinding.LegacyViewBinding
import com.squareup.sample.compose.nestedrenderings.RecursiveWorkflow.LegacyRendering
import com.squareup.sample.compose.nestedrenderings.RecursiveWorkflow.Rendering
import com.squareup.sample.compose.nestedrenderings.RecursiveWorkflow.State
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

/**
 * A simple workflow that produces [Rendering]s of zero or more children.
 * The rendering provides event handlers for adding children and resetting child count to zero.
 *
 * Every other (odd) rendering in the [Rendering.children] will be wrapped with a [LegacyRendering]
 * to force it to go through the legacy view layer. This way this sample both demonstrates pass-
 * through Composable renderings as well as adapting in both directions.
 */
@OptIn(WorkflowExperimentalApi::class)
object RecursiveWorkflow : StatefulWorkflow<Unit, State, Unit, Screen>() {

  data class State(
    val children: Int = 0,
    val flashTrigger: Int = 0,
    val nextFlashId: Int = 0,
    val pendingFlashes: List<Int> = emptyList(),
  )

  /**
   * A rendering from a [RecursiveWorkflow].
   *
   * @param children A list of renderings to display as children of this rendering.
   * @param onAddChildClicked Adds a child to [children].
   * @param onResetClicked Resets [children] to an empty list.
   */
  data class Rendering(
    val children: List<Screen>,
    val flashTrigger: Int = 0,
    val flashTime: Duration = ZERO,
    val onSelfClicked: () -> Unit = {},
    val onAddChildClicked: () -> Unit,
    val onResetClicked: () -> Unit
  ) : Screen

  /**
   * Wrapper around a [Rendering] that will be implemented using a legacy view.
   */
  data class LegacyRendering(
    val rendering: Screen
  ) : AndroidScreen<LegacyRendering> {
    override val viewFactory = ScreenViewFactory.fromViewBinding(
      LegacyViewBinding::inflate,
      ::LegacyRunner
    )
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?,
  ): State = State()

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext<Unit, State, Unit>
  ): Rendering {
    renderState.pendingFlashes.fastForEach {
      context.runningSideEffect("flash[$it]") {
        delay(0.1.seconds)
        context.actionSink.send(flashImmediately(id = it))
      }
    }

    return Rendering(
      children = List(renderState.children) { i ->
        val child = context.renderChild(
          RecursiveWorkflow,
          key = i.toString(),
          // When a child is clicked, cascade the flash up.
          handler = { flashAfterDelay() }
        )
        if (i % 2 == 0) child else LegacyRendering(child)
      },
      flashTrigger = renderState.flashTrigger,
      flashTime = 0.5.seconds,
      // Trigger a cascade of flashes when clicked.
      onSelfClicked = { context.actionSink.send(flashImmediately()) },
      onAddChildClicked = { context.actionSink.send(addChild()) },
      onResetClicked = { context.actionSink.send(resetChildren()) }
    )
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun addChild() = action("addChild") {
    state = state.copy(children = state.children + 1)
  }

  private fun resetChildren() = action("resetChildren") {
    state = state.copy(
      children = 0,
      pendingFlashes = emptyList(),
    )
  }

  private fun flashImmediately(id: Int? = null) = action("triggerImmediateFlash[id=$id]") {
    val newPendingFlashes = if (id == null) state.pendingFlashes else state.pendingFlashes - id
    state = state.copy(
      flashTrigger = state.flashTrigger + 1,
      pendingFlashes = newPendingFlashes,
    )
    setOutput(Unit)
  }

  private fun flashAfterDelay() = action("triggerDelayedFlash") {
    val newFlash = state.nextFlashId
    state = state.copy(
      nextFlashId = state.nextFlashId + 1,
      pendingFlashes = state.pendingFlashes + newFlash
    )
  }
}
