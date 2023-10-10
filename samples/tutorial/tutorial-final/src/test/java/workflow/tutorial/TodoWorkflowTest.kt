package workflow.tutorial

import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.testing.expectWorkflow
import com.squareup.workflow1.testing.testRender
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import workflow.tutorial.TodoEditWorkflow.Output.Save
import workflow.tutorial.TodoListWorkflow.Output.SelectTodo
import workflow.tutorial.TodoWorkflow.State
import workflow.tutorial.TodoWorkflow.State.Step.Edit
import workflow.tutorial.TodoWorkflow.State.Step.List
import workflow.tutorial.TodoWorkflow.TodoProps
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(WorkflowUiExperimentalApi::class)
class TodoWorkflowTest {

  @Test fun `selecting todo`() {
    val todos = listOf(TodoModel(title = "Title", note = "Note"))

    TodoWorkflow
      .testRender(
        props = TodoProps(name = "Ada"),
        // Start from the list step to validate selecting a todo.
        initialState = State(
          todos = todos,
          step = List
        )
      )
      // We only expect the TodoListWorkflow to be rendered.
      .expectWorkflow(
        workflowType = TodoListWorkflow::class,
        rendering = TodoListScreen(
          username = "",
          todoTitles = listOf("Title"),
          onTodoSelected = {},
          onBackClick = {},
          onAddClick = {}
        ),
        // Simulate selecting the first todo.
        output = WorkflowOutput(SelectTodo(index = 0))
      )
      .render { rendering ->
        // Just validate that there is one item in the back stack.
        // Additional validation could be done on the screens returned, if desired.
        assertEquals(1, rendering.size)
      }
      // Assert that the state was updated after the render pass with the output from the
      // TodoListWorkflow.
      .verifyActionResult { newState, _ ->
        assertEqualState(
          State(
            todos = listOf(TodoModel(title = "Title", note = "Note")),
            step = Edit(0)
          ), newState
        )
      }
  }

  @Test fun `saving todo`() {
    val todos = listOf(TodoModel(title = "Title", note = "Note"))

    TodoWorkflow
      .testRender(
        props = TodoProps(name = "Ada"),
        // Start from the edit step so we can simulate saving.
        initialState = State(
          todos = todos,
          step = Edit(index = 0)
        )
      )
      // We always expect the TodoListWorkflow to be rendered.
      .expectWorkflow(
        workflowType = TodoListWorkflow::class,
        rendering = TodoListScreen(
          username = "",
          todoTitles = listOf("Title"),
          onTodoSelected = {},
          onBackClick = {},
          onAddClick = {}
        )
      )
      // Expect the TodoEditWorkflow to be rendered as well (as we're on the edit step).
      .expectWorkflow(
        workflowType = TodoEditWorkflow::class,
        rendering = TodoEditScreen(
          title = TextController("Title"),
          note = TextController("Note"),
          onBackClick = {},
          onSaveClick = {}
        ),
        // Simulate it emitting an output of `.save` to update the state.
        output = WorkflowOutput(
          Save(
            TodoModel(
              title = "Updated Title",
              note = "Updated Note"
            )
          )
        )
      )
      .render { rendering ->
        // Just validate that there are two items in the back stack.
        // Additional validation could be done on the screens returned, if desired.
        assertEquals(2, rendering.size)
      }
      // Validate that the state was updated after the render pass with the output from the
      // TodoEditWorkflow.
      .verifyActionResult { newState, _ ->
        assertEqualState(
          State(
            todos = listOf(TodoModel(title = "Updated Title", note = "Updated Note")),
            step = List
          ),
          newState
        )
      }
  }

  private fun assertEqualState(expected: State, actual: State) {
    assertEquals(expected.todos.size, actual.todos.size)
    expected.todos.forEachIndexed { index, todo ->
      assertEquals(
        expected.todos[index].title.textValue,
        actual.todos[index].title.textValue,
        "todos[$index].title"
      )
      assertEquals(
        expected.todos[index].note.textValue,
        actual.todos[index].note.textValue,
        "todos[$index].note"
      )
    }
    assertEquals(expected.step, actual.step)
  }
}
