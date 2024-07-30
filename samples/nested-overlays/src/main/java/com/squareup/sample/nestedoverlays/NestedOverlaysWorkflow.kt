@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.sample.nestedoverlays

import com.squareup.sample.nestedoverlays.NestedOverlaysWorkflow.State
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironment.Companion
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.withComposeInteropSupport
import com.squareup.workflow1.ui.navigation.BackButtonScreen
import com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.navigation.FullScreenModal
import com.squareup.workflow1.ui.withEnvironment

@OptIn(WorkflowUiExperimentalApi::class)
object NestedOverlaysWorkflow : StatefulWorkflow<Unit, State, Nothing, Screen>() {
  data class State(
    val forceCompose: Boolean = false,
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
    context: RenderContext
  ): Screen {
    if (renderState.nuked) {
      return ButtonBar(Button(R.string.reset, context.eventHandler { state = State() }))
    }

    val forceComposeButton = Button(
      name = if (renderState.forceCompose) R.string.using_compose else R.string.use_compose,
      onClick = context.eventHandler {
        state = state.copy(forceCompose = !renderState.forceCompose)
      }
    )

    val toggleTopBarButton = Button(
      name = if (renderState.showTopBar) R.string.hide_top else R.string.show_top,
      onClick = context.eventHandler { state = state.copy(showTopBar = !state.showTopBar) }
    )

    val toggleBottomBarButton = Button(
      name = if (renderState.showBottomBar) R.string.hide_bottom else R.string.show_bottom,
      onClick = context.eventHandler { state = state.copy(showBottomBar = !state.showBottomBar) }
    )

    val outerSheet = if (!renderState.showOuterSheet) {
      null
    } else {
      val closeOuter = context.eventHandler { state = state.copy(showOuterSheet = false) }
      val content = BackButtonScreen(
        ButtonBar(
          Button(
            name = R.string.close,
            onClick = closeOuter
          ),
          context.toggleInnerSheetButton(renderState),
          color = android.R.color.holo_green_light,
          showEditText = true,
        ),
        onBackPressed = closeOuter
      )
      FullScreenModal(
        if (renderState.forceCompose) ForceComposeWrapper(content) else content
      )
    }

    val innerSheet = if (!renderState.showInnerSheet) {
      null
    } else {
      val closeInner = context.eventHandler { state = state.copy(showInnerSheet = false) }
      val content = BackButtonScreen(
        ButtonBar(
          Button(
            name = R.string.close,
            onClick = closeInner
          ),
          forceComposeButton,
          toggleTopBarButton,
          toggleBottomBarButton,
          Button(
            name = R.string.nuke,
            onClick = context.eventHandler { state = State(nuked = true) }
          ),
          color = android.R.color.holo_red_light,
          showEditText = true
        ),
        onBackPressed = closeInner
      )
      FullScreenModal(
        if (renderState.forceCompose) ForceComposeWrapper(content) else content
      )
    }
    val bodyBarButtons = ButtonBar(forceComposeButton, toggleTopBarButton, toggleBottomBarButton)

    val realBody = TopAndBottomBarsScreen(
      topBar = if (!renderState.showTopBar) null else context.topBottomBar(renderState),
      content = BodyAndOverlaysScreen(
        name = "inner",
        body = bodyBarButtons,
        overlays = listOfNotNull(innerSheet)
      ),
      bottomBar = if (!renderState.showBottomBar) null else context.topBottomBar(renderState)
    )

    val maybeWrappedBody =
      if (renderState.forceCompose) ForceComposeWrapper(realBody) else realBody

    return BodyAndOverlaysScreen(
      name = "outer",
      overlays = listOfNotNull(outerSheet),
      body = maybeWrappedBody
    ).withEnvironment(ViewEnvironment.EMPTY.withComposeInteropSupport())
  }

  override fun snapshotState(state: State) = null

  private fun RenderContext.topBottomBar(
    renderState: State
  ) = ButtonBar(
    toggleInnerSheetButton(renderState),
    Button(
      name = R.string.cover_all,
      onClick = eventHandler { state = state.copy(showOuterSheet = true) }
    )
  )

  private fun RenderContext.toggleInnerSheetButton(renderState: State) =
    Button(
      name = if (renderState.showInnerSheet) R.string.reveal_body else R.string.cover_body,
      onClick = eventHandler {
        state = state.copy(showInnerSheet = !state.showInnerSheet)
      }
    )
}
