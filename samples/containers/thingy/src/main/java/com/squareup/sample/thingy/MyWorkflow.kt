package com.squareup.sample.thingy

import com.squareup.workflow1.Workflow
import com.squareup.workflow1.ui.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

enum class MyOutputs {
  Back,
  Done,
}

data class RetryScreen(
  val message: String,
  val onRetryClicked: () -> Unit,
  val onCancelClicked: () -> Unit,
) : Screen

data object LoadingScreen : Screen

@Suppress("NAME_SHADOWING")
class MyWorkflow(
  private val child1: Workflow<Unit, String, Screen>,
  private val child2: Workflow<Unit, String, Screen>,
  private val child3: Workflow<String, Nothing, Screen>,
  private val networkCall: suspend (String) -> String
) : BackStackWorkflow<String, MyOutputs>() {

  override suspend fun BackStackScope.runBackStack(
    props: StateFlow<String>,
    emitOutput: (MyOutputs) -> Unit
  ) {
    // Step 1
    showWorkflow(child1) { output ->
      when (output) {
        "back" -> emitOutput(MyOutputs.Back)
        "next" -> {
          // Step 2
          val childResult = showWorkflow(child2) { output ->
              // Removes child2 from the stack, cancels the output handler from step 1, and just
              // leaves child1 rendering.
            if (output == "back") cancelWorkflow()
            output
          }

          // Step 3 – make a network call, showing a retry screen if it fails. If the user cancels
          // instead of retrying, we go back to showing child1.
          val networkResult = networkCallWithRetry(childResult)

          // Step 4: Show a workflow for 3 seconds then finish.
          launch {
            delay(3.seconds)
            emitOutput(MyOutputs.Done)
          }
          showWorkflow(child3, networkResult)
        }

        else -> error("Unexpected output: $output")
      }
    }
  }

  override fun getBackStackFactory(coroutineContext: CoroutineContext): BackStackFactory =
    BackStackFactory.showLoadingScreen { LoadingScreen }

  private suspend fun BackStackParentScope.networkCallWithRetry(
    request: String
  ): String {
    var networkResult = networkCall(request)
    while (networkResult == "failure") {
      showScreen {
        RetryScreen(
          message = networkResult,
          onRetryClicked = { continueWith(Unit) },
          // Go back to showing child1.
          onCancelClicked = { cancelScreen() }
        )
      }
      networkResult = networkCall(request)
    }
    return networkResult
  }
}
