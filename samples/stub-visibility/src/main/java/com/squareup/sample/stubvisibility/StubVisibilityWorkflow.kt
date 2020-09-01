package com.squareup.sample.stubvisibility

import com.squareup.sample.stubvisibility.StubVisibilityWorkflow.Outer
import com.squareup.sample.stubvisibility.StubVisibilityWorkflow.State
import com.squareup.sample.stubvisibility.StubVisibilityWorkflow.State.HideBottom
import com.squareup.sample.stubvisibility.StubVisibilityWorkflow.State.ShowBottom
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.action
import com.squareup.workflow1.parse

object StubVisibilityWorkflow : StatefulWorkflow<Unit, State, Nothing, Outer>() {
  enum class State {
    HideBottom,
    ShowBottom
  }

  data class Outer(
    val top: ClickyText,
    val bottom: ClickyText
  )

  data class ClickyText(
    val message: String,
    val visible: Boolean = true,
    val onClick: (() -> Unit)? = null
  )

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = snapshot
      ?.bytes
      ?.parse { source -> if (source.readInt() == 1) HideBottom else ShowBottom }
      ?: HideBottom

  override fun render(
    props: Unit,
    state: State,
    context: RenderContext
  ): Outer = when (state) {
    HideBottom -> Outer(
        top = ClickyText(message = "Click to show footer") {
          context.actionSink.send(action {
            this@action.state = ShowBottom
          })
        },
        bottom = ClickyText(message = "Should not be seen", visible = false)
    )
    ShowBottom -> Outer(
        top = ClickyText(message = "Click to hide footer") {
          context.actionSink.send(action {
            this@action.state = HideBottom
          })
        },
        bottom = ClickyText(message = "Footer", visible = true)
    )
  }

  override fun snapshotState(state: State): Snapshot =
    Snapshot.of(if (state == HideBottom) 1 else 0)
}
