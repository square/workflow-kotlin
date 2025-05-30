package workflow.tutorial

import com.squareup.workflow1.testing.testRender
import org.junit.Test
import workflow.tutorial.TodoEditWorkflow.Output.DiscardChanges
import workflow.tutorial.TodoEditWorkflow.Output.SaveChanges
import workflow.tutorial.TodoEditWorkflow.EditProps
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TodoEditWorkflowTest {
  @Test fun `save emits model`() {
    // Start with a todo of "Title" "Note"
    val props = EditProps(TodoModel(title = "Title", note = "Note"))

    TodoEditWorkflow.testRender(props)
      .render { screen ->
        screen.title.textValue = "New title"
        screen.note.textValue = "New note"
        screen.onSavePressed()
      }.verifyActionResult { _, output ->
        val expected = SaveChanges(TodoModel(title = "New title", note = "New note"))
        assertEquals(expected, output?.value)
      }
  }

  @Test fun `back press discards`() {
    val props = EditProps(TodoModel(title = "Title", note = "Note"))

    TodoEditWorkflow.testRender(props)
      .render { screen ->
        screen.onBackPressed()
      }.verifyActionResult { _, output ->
        assertSame(DiscardChanges, output?.value)
      }
  }
}
