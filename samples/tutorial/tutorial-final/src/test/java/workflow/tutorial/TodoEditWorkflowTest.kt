package workflow.tutorial

import com.squareup.workflow1.applyTo
import org.junit.Test
import workflow.tutorial.TodoEditWorkflow.EditProps
import workflow.tutorial.TodoEditWorkflow.Output.Save
import workflow.tutorial.TodoEditWorkflow.State
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TodoEditWorkflowTest {

  // Start with a todo of "Title" "Note"
  private val startState = State(todo = TodoModel(title = "Title", note = "Note"))

  @Test fun `title is updated`() {
    // These will be ignored by the action.
    val props = EditProps(TodoModel(title = "", note = ""))

    // Update the title to "Updated Title"
    val (newState, actionApplied) = TodoEditWorkflow.onTitleChanged("Updated Title")
        .applyTo(props, startState)

    assertNull(actionApplied.output)
    assertEquals(TodoModel(title = "Updated Title", note = "Note"), newState.todo)
  }

  @Test fun `note is updated`() {
    // These will be ignored by the action.
    val props = EditProps(TodoModel(title = "", note = ""))

    // Update the note to "Updated Note"
    val (newState, actionApplied) = TodoEditWorkflow.onNoteChanged("Updated Note")
        .applyTo(props, startState)

    assertNull(actionApplied.output)
    assertEquals(TodoModel(title = "Title", note = "Updated Note"), newState.todo)
  }

  @Test fun `save emits model`() {
    val props = EditProps(TodoModel(title = "Title", note = "Note"))

    val (_, actionApplied) = TodoEditWorkflow.onSave()
        .applyTo(props, startState)

    assertEquals(Save(TodoModel(title = "Title", note = "Note")), actionApplied.output?.value)
  }

  @Test fun `changed props updated local state`() {
    val initialProps = EditProps(initialTodo = TodoModel(title = "Title", note = "Note"))
    var state = TodoEditWorkflow.initialState(initialProps, null)

    // The initial state is a copy of the provided todo:
    assertEquals("Title", state.todo.title)
    assertEquals("Note", state.todo.note)

    // Create a new internal state, simulating the change from actions:
    state = State(TodoModel(title = "Updated Title", note = "Note"))

    // Update the workflow properties with the same value. The state should not be updated:
    state = TodoEditWorkflow.onPropsChanged(initialProps, initialProps, state)
    assertEquals("Updated Title", state.todo.title)
    assertEquals("Note", state.todo.note)

    // The parent provided different properties. The internal state should be updated with the
    // newly-provided properties.
    val updatedProps = EditProps(initialTodo = TodoModel(title = "New Title", note = "New Note"))
    state = TodoEditWorkflow.onPropsChanged(initialProps, updatedProps, state)
    assertEquals("New Title", state.todo.title)
    assertEquals("New Note", state.todo.note)
  }
}
