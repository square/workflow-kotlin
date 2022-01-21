package com.squareup.sample.stubvisibility

import com.squareup.sample.stubvisibility.StubVisibilityWorkflow.State
import com.squareup.sample.stubvisibility.StubVisibilityWorkflow.State.HideBottom
import com.squareup.sample.stubvisibility.StubVisibilityWorkflow.State.ShowBottom
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse

object StubVisibilityWorkflow : StatefulWorkflow<Unit, State, Nothing, OuterRendering>() {
  enum class State {
    HideBottom,
    ShowBottom
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = snapshot
    ?.bytes
    ?.parse { source -> if (source.readInt() == 1) HideBottom else ShowBottom }
    ?: HideBottom

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): OuterRendering = when (renderState) {
    HideBottom -> OuterRendering(
      top = ClickyTextRendering(message = "Click to show footer") {
        context.actionSink.send(
          action {
            this@action.state = ShowBottom
          }
        )
      },
      bottom = ClickyTextRendering(message = "Should not be seen", visible = false)
    )
    ShowBottom -> OuterRendering(
      top = ClickyTextRendering(message = "Click to hide footer") {
        context.actionSink.send(
          action {
            this@action.state = HideBottom
          }
        )
      },
      bottom = ClickyTextRendering(message = "Footer", visible = true)
    )
  }

  override fun snapshotState(state: State): Snapshot =
    Snapshot.of(if (state == HideBottom) 1 else 0)
}
