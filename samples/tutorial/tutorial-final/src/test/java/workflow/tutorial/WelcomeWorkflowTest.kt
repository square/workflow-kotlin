package workflow.tutorial

import com.squareup.workflow1.applyTo
import com.squareup.workflow1.testing.testRender
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Test
import workflow.tutorial.WelcomeWorkflow.LoggedIn
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(WorkflowUiExperimentalApi::class)
class WelcomeWorkflowTest {

  // region Actions

  @Test fun `login works`() {
    val startState = WelcomeWorkflow.State(TextController("myName"))
    val action = WelcomeWorkflow.onLogin()
    val (_, actionApplied) = action.applyTo(state = startState, props = Unit)

    // Now a LoggedIn output should be emitted when the onLogin action was received.
    assertEquals(LoggedIn("myName"), actionApplied.output?.value)
  }

  @Test fun `login does nothing when name is empty`() {
    val startState = WelcomeWorkflow.State(TextController(""))
    val action = WelcomeWorkflow.onLogin()
    val (state, actionApplied) = action.applyTo(state = startState, props = Unit)

    // Since the name is empty, onLogin will not emit an output.
    assertNull(actionApplied.output)
    // The name is empty, as was specified in the initial state.
    assertEquals("", state.name.textValue)
  }

  // endregion

  // region Rendering

  @Test fun `rendering initial`() {
    // Use the initial state provided by the welcome workflow.
    WelcomeWorkflow.testRender(props = Unit)
        .render { screen ->
          assertEquals("", screen.username.textValue)

          // Simulate tapping the log in button. No output will be emitted, as the name is empty.
          screen.onLoginTapped()
        }
        .verifyActionResult { _, output ->
          assertNull(output)
        }
  }

  @Test fun `rendering login`() {
    // Start with a name already entered.
    WelcomeWorkflow
      .testRender(
        initialState = WelcomeWorkflow.State(name = TextController("Ada")),
        props = Unit
      )
      // Simulate a log in button tap.
      .render { screen ->
        screen.onLoginTapped()
      }
      // Finally, validate that LoggedIn was sent.
      .verifyActionResult { _, output ->
        assertEquals(LoggedIn("Ada"), output?.value)
      }
  }

  // endregion
}
