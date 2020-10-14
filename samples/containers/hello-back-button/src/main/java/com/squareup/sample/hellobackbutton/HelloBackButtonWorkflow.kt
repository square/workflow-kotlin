package com.squareup.sample.hellobackbutton

import android.os.Parcelable
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.Rendering
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Able
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Baker
import com.squareup.sample.hellobackbutton.HelloBackButtonWorkflow.State.Charlie
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.ui.toParcelable
import com.squareup.workflow1.ui.toSnapshot
import kotlinx.android.parcel.Parcelize

object HelloBackButtonWorkflow : StatefulWorkflow<Unit, State, Nothing, Rendering>() {
  @Parcelize
  enum class State : Parcelable {
    Able,
    Baker,
    Charlie;
  }

  data class Rendering(
    val message: String,
    val onClick: () -> Unit,
    val onBackPressed: (() -> Unit)?
  )

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = snapshot?.toParcelable() ?: Able

  override fun render(
    props: Unit,
    state: State,
    context: RenderContext
  ): Rendering {
    return Rendering(
        message = "$state",
        onClick = context.eventHandler {
          this.state = when (this.state) {
            Able -> Baker
            Baker -> Charlie
            Charlie -> Able
          }
        },
        onBackPressed = if (state == Able) null else context.eventHandler {
          this.state = when (this.state) {
            Able -> throw IllegalStateException()
            Baker -> Able
            Charlie -> Baker
          }
        }
    )
  }

  override fun snapshotState(state: State): Snapshot = state.toSnapshot()
}
