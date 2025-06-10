package com.squareup.sample.nestedoverlays

import com.squareup.sample.nestedoverlays.NestedOverlaysWorkflow.State
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.BackButtonScreen
import com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.navigation.FullScreenModal

object NestedOverlaysWorkflow : StatefulWorkflow<Unit, State, Nothing, Screen>() {
  data class State(
    val showTopBar: Boolean = true,
    val showBottomBar: Boolean = true,
    val showInnerSheet: Boolean = false,
    val showOuterSheet: Boolean = false,
    val nuked: Boolean = false
  )

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ) = State()

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext<Unit, State, Nothing>
  ): Screen {
    if (renderState.nuked) {
      return ButtonBar(Button(R.string.reset, context.eventHandler("reset") { state = State() }))
    }

    val toggleTopBarButton = Button(
      name = if (renderState.showTopBar) R.string.hide_top else R.string.show_top,
      onClick = context.eventHandler("show / hide top") {
        state = state.copy(showTopBar = !state.showTopBar)
      }
    )

    val toggleBottomBarButton = Button(
      name = if (renderState.showBottomBar) R.string.hide_bottom else R.string.show_bottom,
      onClick = context.eventHandler("show / hide bottom") {
        state = state.copy(showBottomBar = !state.showBottomBar)
      }
    )

    val outerSheet = if (!renderState.showOuterSheet) {
      null
    } else {
      val closeOuter = context.eventHandler("closeOuter") {
        state = state.copy(showOuterSheet = false)
      }
      FullScreenModal(
        BackButtonScreen(
          ButtonBar(
            Button(
              name = R.string.close,
              onClick = closeOuter
            ),
            context.toggleInnerSheetButton(name = "inner", renderState),
            color = android.R.color.holo_green_light,
            showEditText = true,
          ),
          onBackPressed = closeOuter
        )
      )
    }

    val innerSheet = if (!renderState.showInnerSheet) {
      null
    } else {
      val closeInner = context.eventHandler("closeInner") {
        state = state.copy(showInnerSheet = false)
      }
      FullScreenModal(
        BackButtonScreen(
          ButtonBar(
            Button(
              name = R.string.close,
              onClick = closeInner
            ),
            toggleTopBarButton,
            toggleBottomBarButton,
            Button(
              name = R.string.nuke,
              onClick = context.eventHandler("nuke") { state = State(nuked = true) }
            ),
            color = android.R.color.holo_red_light,
            showEditText = true
          ),
          onBackPressed = closeInner
        )
      )
    }
    val bodyBarButtons = ButtonBar(toggleTopBarButton, toggleBottomBarButton)

    return BodyAndOverlaysScreen(
      name = "outer",
      overlays = listOfNotNull(outerSheet),
      body = TopAndBottomBarsScreen(
        topBar = if (!renderState.showTopBar) {
          null
        } else {
          context.topBottomBar(
            top = true,
            renderState
          )
        },
        content = BodyAndOverlaysScreen(
          name = "inner",
          body = bodyBarButtons,
          overlays = listOfNotNull(innerSheet)
        ),
        bottomBar = if (!renderState.showBottomBar) {
          null
        } else {
          context.topBottomBar(
            top = false,
            renderState
          )
        }
      )
    )
  }

  override fun snapshotState(state: State) = null

  private fun RenderContext<Unit, State, Nothing>.topBottomBar(
    top: Boolean,
    renderState: State
  ): ButtonBar {
    val name = if (top) "top" else "bottom"
    return ButtonBar(
      toggleInnerSheetButton(
        name = name,
        renderState = renderState,
      ),
      Button(
        name = R.string.cover_all,
        onClick = eventHandler("$name cover everything") {
          state = state.copy(showOuterSheet = true)
        }
      )
    )
  }

  private fun RenderContext<Unit, State, Nothing>.toggleInnerSheetButton(
    name: String,
    renderState: State
  ) =
    Button(
      name = if (renderState.showInnerSheet) R.string.reveal_body else R.string.cover_body,
      onClick = eventHandler("$name: reveal / cover body") {
        state = state.copy(showInnerSheet = !state.showInnerSheet)
      }
    )
}
