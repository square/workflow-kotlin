package com.squareup.sample.compose.mutablestateworkflow

import androidx.compose.runtime.mutableStateOf
import com.squareup.sample.compose.mutablestateworkflow.HelloMutableStateWorkflow.Rendering
import com.squareup.sample.compose.mutablestateworkflow.MutableStateWorkflow.Scope
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * TODO write documentation
 */
@OptIn(WorkflowUiExperimentalApi::class)
object HelloMutableStateWorkflow : MutableStateWorkflow<Unit, Nothing, Rendering> {

  class Rendering(
    val text: String,
    val onClick: () -> Unit
  )

  override fun Scope.render(props: Unit): Rendering {
    var counter by rememberSaveable("counter") { mutableStateOf(0) }
    return Rendering(
      text = "Counter: $counter",
      onClick = { counter++ }
    )
  }
}
