@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.sample.nestedoverlays

import com.squareup.sample.nestedoverlays.NestedOverlaysWorkflow.State
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.container.FullScreenOverlay
import com.squareup.workflow1.ui.container.Overlay

typealias NestedOverlaysRendering = BodyAndOverlaysScreen<
  TopAndBottomBarsScreen<BodyAndOverlaysScreen<Screen, Overlay>>,
  Overlay
  >

object NestedOverlaysWorkflow : StatefulWorkflow<Unit, State, Nothing, NestedOverlaysRendering>() {
  data class State(
    val showTopBar: Boolean = true,
    val showBottomBar: Boolean = true,
    val showInnerSheet: Boolean = false,
    val showOuterSheet: Boolean = false
  )

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ) = State()

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): NestedOverlaysRendering {
    val toggleTopBarButton = Button(
      name = if (renderState.showTopBar) R.string.HIDE_TOP else R.string.SHOW_TOP,
      onClick = context.eventHandler { state = state.copy(showTopBar = !state.showTopBar) }
    )

    val toggleBottomBarButton = Button(
      name = if (renderState.showBottomBar) R.string.HIDE_BOTTOM else R.string.SHOW_BOTTOM,
      onClick = context.eventHandler { state = state.copy(showBottomBar = !state.showBottomBar) }
    )

    val outerSheet = if (!renderState.showOuterSheet) {
      null
    } else {
      FullScreenOverlay(
        ButtonBar(
          Button(
            name = R.string.CLOSE,
            onClick = context.eventHandler { state = state.copy(showOuterSheet = false) }
          ),
          context.toggleInnerSheetButton(renderState),
          color = android.R.color.holo_green_light
        )
      )
    }

    val innerSheet = if (!renderState.showInnerSheet) {
      null
    } else {
      FullScreenOverlay(
        ButtonBar(
          Button(
            name = R.string.CLOSE,
            onClick = context.eventHandler { state = state.copy(showInnerSheet = false) }
          ),
          toggleTopBarButton,
          toggleBottomBarButton,
          color = android.R.color.holo_red_light
        )
      )
    }
    val bodyBarButtons = ButtonBar(toggleTopBarButton, toggleBottomBarButton)

    return BodyAndOverlaysScreen(
      name = "outer",
      overlays = listOfNotNull(outerSheet),
      body = TopAndBottomBarsScreen(
        topBar = if (!renderState.showTopBar) null else context.topBottomBar(renderState),
        content = BodyAndOverlaysScreen(
          name = "inner",
          body = bodyBarButtons,
          overlays = listOfNotNull(innerSheet)
        ),
        bottomBar = if (!renderState.showBottomBar) null else context.topBottomBar(renderState)
      )
    )
  }

  override fun snapshotState(state: State) = null

  private fun RenderContext.topBottomBar(
    renderState: State
  ) = ButtonBar(
    toggleInnerSheetButton(renderState),
    Button(
      name = R.string.COVER_ALL,
      onClick = eventHandler { state = state.copy(showOuterSheet = true) }
    )
  )

  private fun RenderContext.toggleInnerSheetButton(renderState: State) =
    Button(
      name = if (renderState.showInnerSheet) R.string.REVEAL_BODY else R.string.COVER_BODY,
      onClick = eventHandler {
        state = state.copy(showInnerSheet = !state.showInnerSheet)
      }
    )
}
