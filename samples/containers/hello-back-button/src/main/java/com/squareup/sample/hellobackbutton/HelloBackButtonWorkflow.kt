package com.squareup.sample.hellobackbutton

import android.os.Parcelable
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Able
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Baker
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Charlie
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.ui.toParcelable
import com.squareup.workflow1.ui.toSnapshot
import kotlinx.parcelize.Parcelize

object HelloBackButtonWorkflow : StatefulWorkflow<
  Unit,
  State,
  Nothing,
  HelloBackButtonScreen
  >() {

  @Parcelize
  enum class State : Parcelable {
    Able,
    Baker,
    Charlie;
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = snapshot?.toParcelable() ?: Able

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): HelloBackButtonScreen {
    return HelloBackButtonScreen(
        message = "$renderState",
        onClick = context.eventHandler {
          state = when (state) {
            Able -> Baker
            Baker -> Charlie
            Charlie -> Able
          }
        },
        onBackPressed = if (renderState == Able) null else context.eventHandler {
          state = when (state) {
            Able -> throw IllegalStateException()
            Baker -> Able
            Charlie -> Baker
          }
        }
    )
  }

  override fun snapshotState(state: State): Snapshot = state.toSnapshot()
}
