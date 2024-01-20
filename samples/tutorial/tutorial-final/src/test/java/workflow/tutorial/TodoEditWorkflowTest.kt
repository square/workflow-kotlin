package workflow.tutorial

import com.squareup.workflow1.applyTo
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Test
import workflow.tutorial.TodoEditWorkflow.EditProps
import workflow.tutorial.TodoEditWorkflow.Output.SaveChanges
import workflow.tutorial.TodoEditWorkflow.State
import kotlin.test.assertEquals

@OptIn(WorkflowUiExperimentalApi::class)
class TodoEditWorkflowTest {

  // Start with a todo of "Title" "Note"
  private val startState = State(todo = TodoModel(title = "Title", note = "Note"))

  @Test fun `save emits model`() {
    val props = EditProps(TodoModel(title = "Title", note = "Note"))

    val (_, actionApplied) = TodoEditWorkflow.requestSave
        .applyTo(props, startState)

    val expected = SaveChanges(TodoModel(title = "Title", note = "Note")).todo
    val actual = (actionApplied.output?.value as SaveChanges).todo
    assertEquals(expected.title.textValue, actual.title.textValue)
    assertEquals(expected.note.textValue, actual.note.textValue)
  }

  @Test fun `changed props updated local state`() {
    val initialProps = EditProps(initialTodo = TodoModel(title = "Title", note = "Note"))
    var state = TodoEditWorkflow.initialState(initialProps, null)

    // The initial state is a copy of the provided todo:
    assertEquals("Title", state.todo.title.textValue)
    assertEquals("Note", state.todo.note.textValue)

    // Create a new internal state, simulating the change from actions:
    state = State(TodoModel(title = "Updated Title", note = "Note"))

    // Update the workflow properties with the same value. The state should not be updated:
    state = TodoEditWorkflow.onPropsChanged(initialProps, initialProps, state)
    assertEquals("Updated Title", state.todo.title.textValue)
    assertEquals("Note", state.todo.note.textValue)

    // The parent provided different properties. The internal state should be updated with the
    // newly-provided properties.
    val updatedProps = EditProps(initialTodo = TodoModel(title = "New Title", note = "New Note"))
    state = TodoEditWorkflow.onPropsChanged(initialProps, updatedProps, state)
    assertEquals("New Title", state.todo.title.textValue)
    assertEquals("New Note", state.todo.note.textValue)
  }
}
