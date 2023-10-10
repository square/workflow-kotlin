package workflow.tutorial

import com.squareup.workflow1.testing.testRender
import com.squareup.workflow1.ui.TextController
import org.junit.Test
import workflow.tutorial.WelcomeWorkflow.LoggedIn
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WelcomeWorkflowTest {

  // region Rendering

  @Test fun `rendering initial`() {
    // Use the initial state provided by the welcome workflow.
    WelcomeWorkflow.testRender(props = Unit)
        .render { screen ->
          assertEquals("", screen.username.textValue)

          // Simulate tapping the log in button. No output will be emitted, as the name is empty.
          screen.onLogInPressed()
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
        screen.onLogInPressed()
      }
      // Finally, validate that LoggedIn was sent.
      .verifyActionResult { _, output ->
        assertEquals(LoggedIn("Ada"), output?.value)
      }
  }

  // endregion
}
