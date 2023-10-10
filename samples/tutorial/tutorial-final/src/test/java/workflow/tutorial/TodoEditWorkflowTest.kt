package workflow.tutorial

import com.squareup.workflow1.applyTo
import com.squareup.workflow1.ui.TextController
import org.junit.Test
import workflow.tutorial.TodoEditWorkflow.EditProps
import workflow.tutorial.TodoEditWorkflow.Output.SaveChanges
import workflow.tutorial.TodoEditWorkflow.State
import kotlin.test.assertEquals

class TodoEditWorkflowTest {

  // Start with a todo of "Title" "Note"
  private val startState = State(
    editedTitle = TextController("Title"),
    editedNote = TextController("Note")
  )

  @Test fun `save emits model`() {
    val props = EditProps(TodoModel(title = "Title", note = "Note"))

    val (_, actionApplied) = TodoEditWorkflow.requestSave
      .applyTo(props, startState)

    val expected = SaveChanges(TodoModel(title = "Title", note = "Note")).todo
    val actual = (actionApplied.output?.value as SaveChanges).todo
    assertEquals(expected.title, actual.title)
    assertEquals(expected.note, actual.note)
  }

  @Test fun `changed props updated local state`() {
    val initialProps = EditProps(initialTodo = TodoModel(title = "Title", note = "Note"))
    var state = TodoEditWorkflow.initialState(initialProps, null)

    // The initial state is a copy of the provided todo:
    assertEquals("Title", state.editedTitle.textValue)
    assertEquals("Note", state.editedNote.textValue)

    // Create a new internal state, simulating the change from actions:
    state = State(
      editedTitle = TextController("Updated Title"),
      editedNote = TextController("Note")
    )

    // The parent provided different properties. The internal state should be updated with the
    // newly-provided properties.
    val updatedProps = EditProps(initialTodo = TodoModel(title = "New Title", note = "New Note"))
    state = TodoEditWorkflow.onPropsChanged(initialProps, updatedProps, state)
    assertEquals("New Title", state.editedTitle.textValue)
    assertEquals("New Note", state.editedNote.textValue)
  }
}
