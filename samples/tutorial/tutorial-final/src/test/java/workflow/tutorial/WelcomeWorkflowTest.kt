package workflow.tutorial

import com.squareup.workflow1.testing.testRender
import org.junit.Test
import workflow.tutorial.WelcomeWorkflow.LoggedIn
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WelcomeWorkflowTest {
  @Test fun `successful log in`() {
    WelcomeWorkflow
      .testRender(props = Unit)
      // Simulate a log in button tap.
      .render { screen ->
        screen.onLogInTapped("Ada")
      }
      // Validate that LoggedIn was sent.
      .verifyActionResult { _, output ->
        assertEquals(LoggedIn("Ada"), output?.value)
      }
  }

  @Test fun `failed log in`() {
    WelcomeWorkflow.testRender(props = Unit)
      .render { screen ->
        // Simulate a log in button tap with an empty name.
        screen.onLogInTapped("")
      }
      .verifyActionResult { _, output ->
        // No output will be emitted, as the name is empty.
        assertNull(output)
      }
      .testNextRender()
      .render { screen ->
        // There is an error prompt.
        assertEquals("name required to log in", screen.promptText)
      }
  }
}
