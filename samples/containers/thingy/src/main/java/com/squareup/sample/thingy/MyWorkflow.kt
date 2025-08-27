package com.squareup.sample.thingy

import com.squareup.workflow1.Workflow
import com.squareup.workflow1.ui.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

enum class MyOutputs {
  Back,
  Done,
}

fun MyWorkflow(
  child1: Workflow<Unit, String, Screen>,
  child2: Workflow<Unit, String, Screen>,
  child3: Workflow<String, Nothing, Screen>,
  networkCall: suspend (String) -> String
) = thingyWorkflow<String, MyOutputs> {

  // Step 1
  showWorkflow(child1) { output ->
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
        showWorkflow(child3, networkResult) {}
      }

      else -> {}
    }
  }
}
