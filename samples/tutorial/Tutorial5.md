# Step 5

_Unit and Integration Testing Workflows_

## Setup

To follow this tutorial, launch Android Studio and open this folder (`samples/tutorial`).

Start from the implementation of `tutorial-4-complete` if you're skipping ahead.

## Testing

`Workflow`s being easily testable was a design requirement.
It is essential to building scalable, reliable software.

The `workflow-testing` library is provided to allow easy unit and integration testing.
For this tutorial, we'll use the `kotlin-test` library to define tests and assertions,
but feel free to use your favorite testing or assertion library instead – `workflow-testing` doesn't care.
(In-house we're very partial to [Truth](https://truth.dev/).)

## Unit Tests: `testRender()` and `RenderTester`

Most of the interesting logic in a `Workflow` implementation will be in its `render()` method,
and so those are also the focus of most of workflow unit testing.
The `testRender` extension on `Workflow` provides an easy way to test the rendering of a workflow.
It returns a `RenderTester` with a fluid API for describing test cases.

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

### WelcomeWorkflow Tests

Start by creating a new unit test directory structure and file: `src/test/java/workflow/tutorial/WelcomeWorkflowTest`.

```kotlin
class WelcomeWorkflowTest {

  @Test fun exampleTest() {
    // TODO
  }
}
```

Update `build.gradle` to include two test dependencies

```groovy
dependencies {
 ...
 testImplementation deps.kotlin.test
 testImplementation deps.workflow.testing
}
```

We will start by testing that the action run on a successful log in
posts the given user name as output.

```kotlin
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
}
```

We have now validated that an output is emitted on log in.
However, while writing this test,
it probably doesn't make sense to allow someone to log in without providing a username.
Let's add a test to ensure that login is only allowed when there is a username:

```kotlin
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
```

Run the test again and ensure that it passes.

### TodoEditWorkflow

The `TodoEditWorkflow` invites two tests, one for each of its possible output values.

```kotlin
class TodoEditWorkflowTest {
  @Test fun `save emits model`() {
    // Start with a todo of "Title" "Note"
    val props = Props(TodoModel(title = "Title", note = "Note"))

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
    val props = Props(TodoModel(title = "Title", note = "Note"))

    TodoEditWorkflow.testRender(props)
      .render { screen ->
        screen.onBackPressed()
      }.verifyActionResult { _, output ->
        assertSame(DiscardChanges, output?.value)
      }
  }
}
```

Add tests against the `render` method of the `TodoListWorkflow` as desired.

> [!TIP]
>
> It is also possible and practical to write unit tests of `WorkflowAction` objects themselves.
> A `WorkflowAction`'s `apply` function is effectively a reducer.
> Given a current state and action, it returns a new state (and optionally an output).
> Because an `apply` function should almost always be a "pure" function,
> it is a great candidate for unit testing.
>
> The `WorkflowAction` class has a single method, `apply`.
> This method is designed to be convenient to _implement_,
> but it's a bit awkward to call since it takes a special receiver.
> To make it easy to test `WorkflowAction`s,
> there is an extension method on `WorkflowAction` called `applyTo`
> that takes a current state and returns the new state and optional output:
>
>  ```kotlin
>  val (newState: State, output: WorkflowOutput<Output>?) = TestedWorkflow.someAction()
>    .applyTo(
>     props = Props(…),
>     state = State(…)
>   )
>
> if (output != null) {
>   // The action set an output.
> } else {
>   // The action did not call setOutput.
> }
> ```
>
> You can use this function to test that your actions perform the correct state transitions
> and emit the correct outputs.
> So why don't we emphasize this technique in the tutorial?
> Because we don't tend to write this kind of of test much ourselves,
> even though we expected them to be our mainstay when we created the workflow library.
> We find that we lean heavily on inline `eventHandler` calls in our `render()` methods,
> and as a result most of our tests are built around `testRender()`.

## Composition Testing

We've demonstrated how to test leaf workflows for their actions and renderings.
However, the power of workflow is the ability to compose a tree of workflows.
The `RenderTester` provides tools to test workflows with children.

`RenderTester.expectWorkflow()` allows us to describe a child workflow
that is expected to be rendered in the next render pass.
It is given the type of child, an optional key, and the fake rendering to return.
It can also provide an optional output, and even a function to validate the props passed by the parent:

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

The full API allows for declaring expected workers and (child) workflows,
as well as verification of resulting state and output:

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

The child's rendering _must_ be specified when declaring an expected workflow
since the parent's call to `renderChild` _must_ return a value of the appropriate rendering type,
and the workflow library can't know how to create those instances of your own types.

> [!NOTE]
> Under `testRender` all children are mocked
>
> We consider tests built around `testRender` to be unit tests (as opposed to integration tests)
> because they do not actually run any child workflows or workers.
> Each call to `expectWorkflow` and is a declaration that a child of a certain type should be invoked,
> and a provider of whatever output (if any) a mock instance of it should provide.
> These tests are meant to focus on the subject workflow itself,
> not the full suite of all of its dependencies.
>
> See [Integration Testing](#integration-testing) below for a discussion of how to test a complete workflow tree.

### RootNavigationWorkflow Tests

The `RootNavigationWorkflow` is responsible for the entire state of our app.

First we can test the `ShowingWelcome` state on its own:

```kotlin
class RootNavigationWorkflowTest {

  @Test fun `welcome rendering`() {
    RootNavigationWorkflow
      // Start in the ShowingWelcome state
      .testRender(initialState = ShowingWelcome, props = Unit)
      // The `WelcomeWorkflow` is expected to be started in this render.
      .expectWorkflow(
        workflowType = WelcomeWorkflow::class,
        rendering = WelcomeScreen(
          promptText = "Well hello there!",
          onLogInTapped = {}
        )
      )
      // Now, validate that there is a single item in the BackStackScreen,
      // which is our welcome screen.
      .render { rendering ->
        val frames = rendering.frames
        assertEquals(1, frames.size)

        val welcomeScreen = frames[0] as WelcomeScreen
        assertEquals("Well hello there!", welcomeScreen.promptText)
      }
      // Assert that no action was produced during this render,
      // meaning our state remains unchanged
      .verifyActionResult { _, output ->
        assertNull(output)
      }
  }
}
```

We can also test the transition from the `Welcome` state to the `Todo` state:

```kotlin
  @Test fun `login event`() {
    RootNavigationWorkflow
      // Start in the Welcome state
      .testRender(initialState = ShowingWelcome, props = Unit)
      // The WelcomeWorkflow is expected to be started in this render.
      .expectWorkflow(
        workflowType = WelcomeWorkflow::class,
        rendering = WelcomeScreen(
          promptText = "yo",
          onLogInTapped = {}
        ),
        // Simulate the WelcomeWorkflow sending an output of LoggedIn
        // as if the "log in" button was tapped.
        output = WorkflowOutput(LoggedIn(username = "Ada"))
      )
      // Now, validate that there is a single item in the BackStackScreen,
      // which is our welcome screen (prior to the output).
      .render { rendering ->
        val backstack = rendering.frames
        assertEquals(1, backstack.size)

        val welcomeScreen = backstack[0] as WelcomeScreen
      }
      // Assert that the state transitioned to Todo.
      .verifyActionResult { newState, _ ->
        assertEquals(ShowingTodo(username = "Ada"), newState)
      }
  }
```

By simulating the output from the `WelcomeWorkflow`,
we were able to drive the `RootNavigationWorkflow` forward.

### TodoNavigationWorkflow Render Tests

Now add tests for the `TodoNavigationWorkflow`,
so that we have relatively full navigation coverage.
These are two examples, of selecting and saving a todo to validate the transitions between screens,
as well as updating the state in the parent:

```kotlin
class TodoNavigationWorkflowTest {

  @Test fun `selecting todo`() {
    val todos = listOf(TodoModel(title = "Title", note = "Note"))

    TodoNavigationWorkflow
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
          onRowPressed = {},
          onBackPressed = {},
          onAddPressed = {}
        ),
        // Simulate selecting the first todo.
        output = WorkflowOutput(TodoSelected(index = 0))
      )
      .render { backstack ->
        // Just validate that there is one item in the back stack.
        // Additional validation could be done on the screens returned, if desired.
        assertEquals(1, backstack.size)
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

    TodoNavigationWorkflow
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
          onRowPressed = {},
          onBackPressed = {},
          onAddPressed = {}
        )
      )
      // Expect the TodoEditWorkflow to be rendered as well (as we're on the edit step).
      .expectWorkflow(
        workflowType = TodoEditWorkflow::class,
        rendering = TodoEditScreen(
          title = TextController("Title"),
          note = TextController("Note"),
          onBackPressed = {},
          onSavePressed = {}
        ),
        // Simulate it emitting an output of `.save` to update the state.
        output = WorkflowOutput(
          SaveChanges(
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
    expected.todos.forEachIndexed { index, _ ->
      assertEquals(
        expected.todos[index].title,
        actual.todos[index].title,
        "todos[$index].title"
      )
      assertEquals(
        expected.todos[index].note,
        actual.todos[index].note,
        "todos[$index].note"
      )
    }
    assertEquals(expected.step, actual.step)
  }
}
```

## Integration Testing

The `RenderTester` allows easy "mocking" of child workflows and workers.
However, this means that we are not exercising the full infrastructure
(even though we could get a fairly high confidence from the tests).
Sometimes, it may be worth putting together integration tests that test a full tree of Workflows.
This lets us test integration with the non-workflow world as well,
such as external reactive data sources that your workflows might be observing via Workers.

> [!TIP]
>
> Integration tests can also be a fast, unflakey alternative to Espresso tests —
> especially when combined with [Paparazzi](https://github.com/cashapp/paparazzi) snapshot tests.

Add another test to `RootNavigationWorkflowTests`.
We will use another test helper that spins up a real instance of the workflow runtime,
the same runtime that `renderWorkflowIn` uses.

### WorkflowTester

When you create an Android app using Workflow,
you will probably use `renderWorkflowIn`,
which starts a runtime to host your workflows in an androidx ViewModel.
Under the hood, this method is an overload of lower-level `renderWorkflowIn` function
that runs the workflow runtime in a coroutine and exposes a `StateFlow` of renderings.
When writing integration tests for workflows,
you can use this core function directly (maybe with a library like [Turbine](https://github.com/cashapp/turbine)),
or you can use `workflow-testing`'s `WorkflowTester`.
The `WorkflowTester` starts a workflow and lets you request renderings and outputs manually
so you can write tests that interact with the runtime from the outside.

This will be a properly opaque test,
as we can only test the behaviors from the rendering and will not be able to inspect the underlying states. This may be a useful test for validation when refactoring a tree of workflows
to ensure they behave the same way.

The main entry point to this API is to call `Workflow.launchForTestingFromStartWith()`
and pass a lambda that implements your test logic.

### RootNavigationWorkflow

Let's use `launchForTestingFromStartWith` to write a general integration test for `RootWorkflow`:

```kotlin
class RootNavigationWorkflowTest {

  // …

  @Test fun `app flow`() {
    RootNavigationWorkflow.launchForTestingFromStartWith {
      // First rendering is just the welcome screen. Update the name.
      awaitNextRendering().let { rendering ->
        assertEquals(1, rendering.frames.size)
        val welcomeScreen = rendering.frames[0] as WelcomeScreen

        // Enter a name and tap login
        welcomeScreen.onLogInTapped("Ada")
      }

      // Expect the todo list to be rendered. Edit the first todo.
      awaitNextRendering().let { rendering ->
        assertEquals(2, rendering.frames.size)
        assertTrue(rendering.frames[0] is WelcomeScreen)
        val todoScreen = rendering.frames[1] as TodoListScreen
        assertEquals(1, todoScreen.todoTitles.size)

        // Select the first todo.
        todoScreen.onRowPressed(0)
      }

      // Selected a todo to edit. Expect the todo edit screen.
      awaitNextRendering().let { rendering ->
        assertEquals(3, rendering.frames.size)
        assertTrue(rendering.frames[0] is WelcomeScreen)
        assertTrue(rendering.frames[1] is TodoListScreen)
        val editScreen = rendering.frames[2] as TodoEditScreen

        // Enter a title and save.
        editScreen.title.textValue = "New Title"
        editScreen.onSavePressed()
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

This test was *very* verbose, and rather long.
Generally we prefer smaller tests built around `testRender`,
with a sprinkling of full integration tests like this
when we need to ensure that services or child workflows are being invoked correctly.

## Conclusion

With this tutorial under your belt you should be ready to write solid tests for your workflow based apps.
These are the same testing patterns we use every day at Square, in every Android app we ship —
along with a lot of [Paparazzi](https://github.com/cashapp/paparazzi) snapshot tests of our `Screen` classes.
