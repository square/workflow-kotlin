# Step 5

_Unit and Integration Testing Workflows_

## Setup

To follow this tutorial, launch Android Studio and open this folder (`samples/tutorial`).

Start from the implementation of `tutorial-4-complete` if you're skipping ahead.

## Testing

`Workflow`s being easily testable was a design requirement. It is essential to building scalable, reliable software.

The `workflow-testing` library is provided to allow easy unit and integration testing. For this tutorial, we'll use the `kotlin-test` library to define tests and assertions, but feel free to use your favorite testing or assertion library instead – `workflow-testing` doesn't care.

## Unit Tests: `WorkflowAction`s

A `WorkflowAction`'s `apply` function is effectively a reducer. Given a current state and action, it returns a new state (and optionally an output). Because an `apply` function should almost always be a "pure" function, it is a great candidate for unit testing.

The `applyTo` extension function is provided to facilitate writing unit tests against actions.

### applyTo

The `WorkflowAction` class has a single method, `apply`. This method is designed to be convenient to _implement_, but it's a bit awkward to call since it takes a special receiver. To make it easy to test `WorkflowAction`s, there is an extension method on `WorkflowAction` called `applyTo` that takes a current state and returns the new state and optional output:

```kotlin
val (newState: State, output: WorkflowOutput<Output>?) = TestedWorkflow.someAction()
  .applyTo(
    props = Props(…),
    state = State(…)
  )

if (output != null) {
  // The action set an output.
} else {
  // The action did not call setOutput.
}
```

You can use this function to test that your actions perform the correct state transitions and emit the correct outputs.

### WelcomeWorkflow Tests

Start by creating a new unit test file called `WelcomeWorkflowTest`.

```kotlin
class WelcomeWorkflowTest {

  @Test fun exampleTest() {
    // TODO
  }
}
```

For the `WelcomeWorkflow`, we will start by testing that the `username` property is updated on the state every time a `onUsernameChanged` action is received:

```kotlin
class WelcomeWorkflowTest {
  @Test fun `username updates`() {
    val startState = WelcomeWorkflow.State("")
    val action = WelcomeWorkflow.onUsernameChanged("myName")
    val (state, output) = action.applyTo(state = startState, props = Unit)

    // No output is expected when the name changes.
    assertNull(output)

    // The name has been updated from the action.
    assertEquals("myName", state.username)
  }
}
```

The `OutputT` of an action can also be tested. Next, we'll add a test for the `onLogin` action.

```kotlin
  @Test fun `login works`() {
    val startState = WelcomeWorkflow.State("myName")
    val action = WelcomeWorkflow.onLogin()
    val (_, output) = action.applyTo(state = startState, props = Unit)

    // Now a LoggedIn output should be emitted when the onLogin action was received.
    assertEquals(LoggedIn("myName"), output?.value)
  }
```

We have now validated that an output is emitted when the `onLogin` action is received. However, while writing this test, it probably doesn't make sense to allow someone to log in without providing a username. Let's update the test to ensure that login is only allowed when there is a username:

```kotlin
  @Test fun `login does nothing when name is empty`() {
    val startState = WelcomeWorkflow.State("")
    val action = WelcomeWorkflow.onLogin()
    val (state, output) = action.applyTo(state = startState, props = Unit)

    // Since the name is empty, onLogin will not emit an output.
    assertNull(output)
    // The name is empty, as was specified in the initial state.
    assertEquals("", state.username)
  }
```

The test will now fail, as a `onLogin` action will still cause `LoggedIn` output when the name is blank. Update the `WelcomeWorkflow` logic to reflect the new behavior we want:

```kotlin
object WelcomeWorkflow : StatefulWorkflow<Unit, State, LoggedIn, WelcomeScreen>() {

  // …

  internal fun onLogin() = action {
    // Don't log in if the name isn't filled in.
    if (state.username.isNotEmpty()) {
      setOutput(LoggedIn(state.username))
    }
  }

  // …

}
```

Run the test again and ensure that it passes. Additionally, run the app to see that it also reflects the updated behavior.

### TodoListWorkflow

We won't write tests for the actions in this workflow, since they don't contain any interesting logic.

### TodoEditWorkflow

The `TodoEditWorkflow` has a bit more complexity since it holds a local copy of the todo to be edited. Start by adding tests for the actions:

```kotlin
class TodoEditWorkflowTest {

  // Start with a todo of "Title" "Note"
  private val startState = State(todo = TodoModel(title = "Title", note = "Note"))

  @Test fun `title is updated`() {
    // These will be ignored by the action.
    val props = EditProps(TodoModel(title = "", note = ""))

    // Update the title to "Updated Title"
    val (newState, output) = TodoEditWorkflow.onTitleChanged("Updated Title")
        .applyTo(props, startState)

    assertNull(output)
    assertEquals(TodoModel(title = "Updated Title", note = "Note"), newState.todo)
  }

  @Test fun `note is updated`() {
    // These will be ignored by the action.
    val props = EditProps(TodoModel(title = "", note = ""))

    // Update the note to "Updated Note"
    val (newState, output) = TodoEditWorkflow.onNoteChanged("Updated Note")
        .applyTo(props, startState)

    assertNull(output)
    assertEquals(TodoModel(title = "Title", note = "Updated Note"), newState.todo)
  }

  @Test fun `save emits model`() {
    val props = EditProps(TodoModel(title = "Title", note = "Note"))

    val (_, output) = TodoEditWorkflow.onSave()
        .applyTo(props, startState)

    assertEquals(Save(TodoModel(title = "Title", note = "Note")), output?.value)
  }
}
```

The `TodoEditWorkflow` also uses the `onPropsChanged` method to update the internal state if its parent provides it with a different `todo`. Validate that this works as expected:

```kotlin
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
```

## Unit Tests: Rendering

Testing actions is very useful for validating all of the state transitions of a workflow, but it is also beneficial to verify the logic in `render`. Since the `render` method uses a private implementation of a `RenderContext`, there is a `RenderTester` to facilitate testing.

### RenderTester

The `testRender` extension on `Workflow` provides an easy way to test the rendering of a workflow. It returns a `RenderTester` with a fluid API for describing test cases.

```kotlin
workflow.testRender(props = Props())
  .render { rendering ->
    assertEquals("expected text on rendering", rendering.text)
  }
```

It also provides a means to test that lambdas passed to screens cause the correct actions and state changes:

```kotlin
workflow.testRender(props = Props())
  .render { rendering ->
    assertEquals("expected text on rendering", rendering.text)
    rendering.updateText("updated")
  }
  .verifyActionResult { newState, output ->
    assertEquals(State(text = "updated"), newState)
  }
```

The full API allows for declaring expected workers and (child) workflows, as well as verification of resulting state and output:
```kotlin
workflow
  .testRender(
    props = Props(),
    initialState = State()
  )
  .expectWorkflow(
    workflowType = ChildWorkflow::class,
    rendering = ChildScreen(),
    output = WorkflowOutput(Closed)
  )
  .expectWorker(
    worker = TestWorker(),
    output = WorkflowOutput(Finished)
  )
  .render { rendering ->
    assertEquals("expected text on rendering", rendering.text)
  }
  .verifyActionResult { newState, output ->
    assertEquals(State(text = "updated"), newState)
    assertEquals(Completed, output?.value)
  }
```

### WelcomeWorkflow

Add tests for the rendering of the `WelcomeWorkflow`:

```kotlin
class WelcomeWorkflowTest {

  // …

  @Test fun `rendering initial`() {
    // Use the initial state provided by the welcome workflow.
    WelcomeWorkflow.testRender(props = Unit)
        .render { screen ->
          assertEquals("", screen.username)

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
          screen.onUsernameChanged("Ada")
        }
        .verifyActionResult { state, _ ->
          // https://github.com/square/workflow-kotlin/issues/230
          assertEquals("Ada", (state as WelcomeWorkflow.State).username)
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
}
```

Add tests against the `render` methods of the `TodoEdit` and `TodoList` workflows as desired.

## Composition Testing

We've demonstrated how to test leaf workflows for their actions and renderings. However, the power of workflow is the ability to compose a tree of workflows. The `RenderTester` provides tools to test workflows with children.

`RenderTester.expectWorkflow()` allows us to describe a child workflow that is expected to be rendered in the next render pass. It is given the type of child, an optional key, and the fake rendering to return. It can also provide an optional output, and even a function to validate the props passed by the parent:

```kotlin
// Type parameters are omitted for demonstration.
fun RenderTester.expectWorkflow(
  workflowType: KClass<out Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>>,
  rendering: ChildRenderingT,
  key: String = "",
  crossinline assertProps: (props: ChildPropsT) -> Unit = {},
  output: WorkflowOutput<ChildOutputT>? = null,
  description: String = ""
)
```

The child's rendering _must_ be specified when declaring an expected workflow since the parent's call to `renderChild` _must_ return a value of the appropriate rendering type, and the workflow library can't know how to create those instances of your own types.

### RootWorkflow Tests

The `RootWorkflow` is responsible for the entire state of our app. We can skip testing the actions explicitly, as that will be handled by testing the rendering.

First we can test the `Welcome` state on its own:

```kotlin
class RootWorkflowTest {

  @Test fun `welcome rendering`() {
    RootWorkflow
        // Start in the Welcome state
        .testRender(initialState = Welcome, props = Unit)
        // The `WelcomeWorkflow` is expected to be started in this render.
        .expectWorkflow(
            workflowType = WelcomeWorkflow::class,
            rendering = WelcomeScreen(
                username = "Ada",
                onUsernameChanged = {},
                onLoginTapped = {}
            )
        )
        // Now, validate that there is a single item in the BackStackScreen, which is our welcome
        // screen.
        .render { rendering ->
          val backstack = (rendering as BackStackScreen<*>).frames
          assertEquals(1, backstack.size)

          val welcomeScreen = backstack[0] as WelcomeScreen
          assertEquals("Ada", welcomeScreen.name)
        }
        // Assert that no action was produced during this render, meaning our state remains unchanged
        .verifyActionResult { _, output ->
          assertNull(output)
        }
  }
}
```

Now, we can also test the transition from the `Welcome` state to the `Todo` state:

```kotlin
  @Test fun `login event`() {
    RootWorkflow
        // Start in the Welcome state
        .testRender(initialState = Welcome, props = Unit)
        // The WelcomeWorkflow is expected to be started in this render.
        .expectWorkflow(
            workflowType = WelcomeWorkflow::class,
            rendering = WelcomeScreen(
                username = "Ada",
                onUsernameChanged = {},
                onLoginTapped = {}
            ),
            // Simulate the WelcomeWorkflow sending an output of LoggedIn as if the "log in" button
            // was tapped.
            output = WorkflowOutput(LoggedIn(username = "Ada"))
        )
        // Now, validate that there is a single item in the BackStackScreen, which is our welcome
        // screen (prior to the output).
        .render { rendering ->
          val backstack = rendering.frames
          assertEquals(1, backstack.size)

          val welcomeScreen = backstack[0] as WelcomeScreen
          assertEquals("Ada", welcomeScreen.username)
        }
        // Assert that the state transitioned to Todo.
        .verifyActionResult { newState, _ ->
          assertEquals(Todo(username = "Ada"), newState)
        }
  }
```

By simulating the output from the `WelcomeWorkflow`, we were able to drive the `RootWorkflow` forward. This was much more of an integration test than a "pure" unit test, but we have now validated the same behavior we see by testing the app by hand.

### TodoWorkflow Render Tests

Now add tests for the `TodoWorkflow`, so that we have relatively full coverage. These are two examples, of selecting and saving a todo to validate the transitions between screens, as well as updating the state in the parent:

```kotlin
class TodoWorkflowTest {

  @Test fun `selecting todo`() {
    val todos = listOf(TodoModel(title = "Title", note = "Note"))

    TodoWorkflow
        .testRender(
            props = TodoProps(username = "Ada"),
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
                onBack = {}
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
          assertEquals(
              State(
                  todos = listOf(TodoModel(title = "Title", note = "Note")),
                  step = Edit(0)
              ),
              newState
          )
        }
  }

  @Test fun `saving todo`() {
    val todos = listOf(TodoModel(title = "Title", note = "Note"))

    TodoWorkflow
        .testRender(
            props = TodoProps(username = "Ada"),
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
                onBack = {}
            )
        )
        // Expect the TodoEditWorkflow to be rendered as well (as we're on the edit step).
        .expectWorkflow(
            workflowType = TodoEditWorkflow::class,
            rendering = TodoEditScreen(
                title = "Title",
                note = "Note",
                onTitleChanged = {},
                onNoteChanged = {},
                discardChanges = {},
                saveChanges = {}
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
          assertEquals(
              State(
                  todos = listOf(TodoModel(title = "Updated Title", note = "Updated Note")),
                  step = List
              ),
              newState
          )
        }
  }
}
```

## Integration Testing

The `RenderTester` allows easy "mocking" of child workflows and workers. However, this means that we are not exercising the full infrastructure (even though we could get a fairly high confidence from the tests). Sometimes, it may be worth putting together integration tests that test a full tree of Workflows. This lets us test integration with the non-workflow world as well, such as external reactive data sources that your workflows might be observing via Workers.

Add another test to `RootWorkflowTests`. We will use another test helper that spins up a real instance of the workflow runtime, the same runtime that `setContentWorkflow` uses.

### WorkflowTester

When you create an Android app using Workflow, you will probably use `Activity.setContentWorkflow`, which starts a runtime to host your workflows, and wires it up to all the Android infrastructure. Under the hood, there's a lower-level API called `renderWorkflowIn` that runs the workflow runtime in a coroutine and exposes a `StateFlow` of renderings. When writing integration tests for workflows, you can use this API directly (maybe with a library like [Turbine](https://github.com/cashapp/turbine)), or you can use `workflow-testing`'s `WorkflowTester`. The `WorkflowTester` starts a workflow and lets you request renderings and outputs manually so you can write tests that interact with the runtime from the outside.

This will be an opaque test, as we can only test the behaviors from the rendering and will not be able to inspect the underlying states. This may be a useful test for validation when refactoring a tree of workflows to ensure they behave the same way.

The main entry point to this API is to call `Workflow.launchForTestingFromStartWith()` and pass a lambda that implements your test logic.

### RootWorkflow

Let's use `launchForTestingFromStartWith` to write a general integration test for `RootWorkflow`:

```kotlin
class RootWorkflowTest {

  // …

  @Test fun `app flow`() {
    RootWorkflow.launchForTestingFromStartWith {
      // First rendering is just the welcome screen. Update the name.
      awaitNextRendering().let { rendering ->
        assertEquals(1, rendering.frames.size)
        val welcomeScreen = rendering.frames[0] as WelcomeScreen

        // Enter a name.
        welcomeScreen.onUsernameChanged("Ada")
      }

      // Log in and go to the todo list.
      awaitNextRendering().let { rendering ->
        assertEquals(1, rendering.frames.size)
        val welcomeScreen = rendering.frames[0] as WelcomeScreen

        welcomeScreen.onLoginTapped()
      }

      // Expect the todo list to be rendered. Edit the first todo.
      awaitNextRendering().let { rendering ->
        assertEquals(2, rendering.frames.size)
        assertTrue(rendering.frames[0] is WelcomeScreen)
        val todoScreen = rendering.frames[1] as TodoListScreen
        assertEquals(1, todoScreen.todoTitles.size)

        // Select the first todo.
        todoScreen.onTodoSelected(0)
      }

      // Selected a todo to edit. Expect the todo edit screen.
      awaitNextRendering().let { rendering ->
        assertEquals(3, rendering.frames.size)
        assertTrue(rendering.frames[0] is WelcomeScreen)
        assertTrue(rendering.frames[1] is TodoListScreen)
        val editScreen = rendering.frames[2] as TodoEditScreen

        // Update the title.
        editScreen.onTitleChanged("New Title")
      }

      // Save the selected todo.
      awaitNextRendering().let { rendering ->
        assertEquals(3, rendering.frames.size)
        assertTrue(rendering.frames[0] is WelcomeScreen)
        assertTrue(rendering.frames[1] is TodoListScreen)
        val editScreen = rendering.frames[2] as TodoEditScreen

        // Save the changes by tapping the save button.
        editScreen.saveChanges()
      }

      // Expect the todo list. Validate the title was updated.
      awaitNextRendering().let { rendering ->
        assertEquals(2, rendering.frames.size)
        assertTrue(rendering.frames[0] is WelcomeScreen)
        val todoScreen = rendering.frames[1] as TodoListScreen

        assertEquals(1, todoScreen.todoTitles.size)
        assertEquals("New Title", todoScreen.todoTitles[0])
      }
    }
  }
}
```

This test was *very* verbose, and rather long. Generally, it's not recommended to do full integration tests like this (the action tests and render tests can give pretty solid coverage of a workflow's behavior). However, this is an example of how it might be done in case it's needed.

## Conclusion

This was intended as a guide of how testing can be facilitated with the `workflow-testing` library provided for workflows. As always, it is up to the judgement of the developer of what and how their software should be tested.
