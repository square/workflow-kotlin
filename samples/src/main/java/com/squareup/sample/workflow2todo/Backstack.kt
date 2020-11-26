package com.squareup.sample.workflow2todo

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.squareup.workflow2.SelfRendering
import com.squareup.workflow2.WorkflowRendering

@Immutable
data class Backstack(val screens: List<WorkflowRendering<SelfRendering>>) : SelfRendering {

  @Composable override fun render() {
    println("OMG Backstack.render()")

    Crossfade(screens.last()) {
      println("OMG Backstack.render rendering screen")
      it.value.render()
    }
  }
}
