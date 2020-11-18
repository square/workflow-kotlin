package workflow.tutorial

import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.testing.testRender
import org.junit.Test
import workflow.tutorial.WelcomeWorkflow.LoggedIn
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalWorkflowApi::class)
class WelcomeWorkflowTest {

  // region Actions

  @Test fun `name updates`() {
    val startState = WelcomeWorkflow.State("")
    val action = WelcomeWorkflow.onNameChanged("myName")
    val (state, output) = action.applyTo(state = startState, props = Unit)

    // No output is expected when the name changes.
    assertNull(output)

    // The name has been updated from the action.
    assertEquals("myName", state.name)
  }

  @Test fun `login works`() {
    val startState = WelcomeWorkflow.State("myName")
    val action = WelcomeWorkflow.onLogin()
    val (_, output) = action.applyTo(state = startState, props = Unit)

    // Now a LoggedIn output should be emitted when the onLogin action was received.
    assertEquals(LoggedIn("myName"), output?.value)
  }

  @Test fun `login does nothing when name is empty`() {
    val startState = WelcomeWorkflow.State("")
    val action = WelcomeWorkflow.onLogin()
    val (state, output) = action.applyTo(state = startState, props = Unit)

    // Since the name is empty, onLogin will not emit an output.
    assertNull(output)
    // The name is empty, as was specified in the initial state.
    assertEquals("", state.name)
  }

  // endregion

  // region Rendering

  @Test fun `rendering initial`() {
    // Use the initial state provided by the welcome workflow.
    WelcomeWorkflow.testRender(props = Unit)
        .render { screen ->
          assertEquals("", screen.name)

          // Simulate tapping the log in button. No output will be emitted, as the name is empty.
          screen.onLoginTapped()
        }
        .verifyActionResult { _, output ->
          assertNull(output)
        }
  }

  @Test fun `rendering name change`() {
    // Use the initial state provided by the welcome workflow.
    WelcomeWorkflow.testRender(props = Unit)
        // Next, simulate the name updating, expecting the state to be changed to reflect the
        // updated name.
        .render { screen ->
          screen.onNameChanged("Ada")
        }
        .verifyActionResult { state, _ ->
          // https://github.com/square/workflow-kotlin/issues/230
          assertEquals("Ada", (state as WelcomeWorkflow.State).name)
        }
  }

  @Test fun `rendering login`() {
    // Start with a name already entered.
    WelcomeWorkflow
      .testRender(
        initialState = WelcomeWorkflow.State(name = "Ada"),
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
