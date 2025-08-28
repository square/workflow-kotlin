package com.squareup.sample.thingy

import com.squareup.workflow1.Workflow
import com.squareup.workflow1.ui.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

enum class MyOutputs {
  Back,
  Done,
}

class MyWorkflow(
  private val child1: Workflow<Unit, String, Screen>,
  private val child2: Workflow<Unit, String, Screen>,
  private val child3: Workflow<String, Nothing, Screen>,
  private val networkCall: suspend (String) -> String
) : BackStackWorkflow<String, MyOutputs>() {

  override suspend fun BackStackScope<MyOutputs>.runBackStack(props: StateFlow<String>) {
    // Step 1
    // TODO clean this up
    val ignored: Unit = showWorkflow(child1) { output ->
      when (output) {
        "back" -> emitOutput(MyOutputs.Back)
        "next" -> {
          // Step 2
          val childResult = showWorkflow(child2) { output ->
            if (output == "back") {
              // Removes child2 from the stack, cancels the output handler from step 1, and just
              // leaves child1 rendering.
              goBack()
            } else {
              finishWith(output)
            }
          }

          // TODO: Show a loading screen automatically.
          val networkResult = networkCall(childResult)

          // Step 3: Show a workflow for 3 seconds then finish.
          launch {
            delay(3.seconds)
            emitOutput(MyOutputs.Done)
          }
          showWorkflow(child3, flowOf(networkResult))
        }

        else -> {}
      }
    }
  }
}
