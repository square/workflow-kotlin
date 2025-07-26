# Step 4

_Refactoring and rebalancing a tree of Workflows_

## Setup

To follow this tutorial, launch Android Studio and open this folder (`samples/tutorial`).

Start from the implementation of `tutorial-3-complete` if you're skipping ahead.

## Refactoring a workflow by splitting it into a parent and child

The `TodoListWorkflow` has started to grow and has multiple concerns it's handling —
specifically all of the `TodoListScreen` behavior,
as well as the actions that can come from the `TodoEditWorkflow`.

When a single workflow seems to be doing too many things,
a common pattern is to extract some of its responsibility into a parent.

### TodoWorkflow

Create a new workflow called `TodoNavigationWorkflow` that will be responsible for
managing both the `TodoListWorkflow` and the `TodoEditWorkflow`.

```kotlin
object TodoNavigationWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Screen>>() {

  // …

}
```

#### Moving logic from the TodoListWorkflow to the TodoNavigationWorkflow

Move the state, input, and outputs from the `TodoListWorkflow` up to the new `TodoNavigationWorkflow`.
It will be the owner the list of todo items,
and the `TodoListWorkflow` will simply show whatever is passed into its input:

```kotlin
object TodoNavigationWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Screen>>() {

  data class TodoProps(val username: String)

  data class State(
    val todos: List<TodoModel>,
    val step: Step
  ) {
    sealed class Step {
      /** Showing the list of items. */
      object List : Step()

      /**
       * Editing a single item. The state holds the index so it can be updated when a save action is
       * received.
       */
      data class Edit(val index: Int) : Step()
    }
  }

  object Back

  override fun initialState(
    props: TodoProps,
    snapshot: Snapshot?
  ) = State(
      todos = listOf(
          TodoModel(
              title = "Take the cat for a walk",
              note = "Cats really need their outside sunshine time. Don't forget to walk " +
                  "Charlie. Hamilton is less excited about the prospect."
          )
      ),
      step = Step.List
  )

  // …
}
```

Define the output events from the `TodoListWorkflow` to include back and a new `SelectTodo` output.
Change its `RenderT` type to `TodoListScreen`.
Also, the todo list will now be provided by our parent via `ListProps`, so we can move it there from `State`.

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Output, TodoListScreen>() {

  data class ListProps(
    val username: String,
    val todos: List<TodoModel>
  )

  object State

  sealed interface Output {
    object BackPressed : Output
    data class TodoSelected(val index: Int) : Output
  }

  override fun initialState(
    props: ListProps,
    snapshot: Snapshot?
  ) = State

  override fun render(
    renderProps: ListProps,
    renderState: State,
    context: RenderContext
  ): TodoListScreen {
    // …
  }

  override fun snapshotState(state: State): Snapshot? = null

  // …
}
```

Change the `onRowPressed` event handler created in `TodoListWorkflow.render()`
to post an output event instead of modifying any state.
Return the `TodoListScreen` and delete the rest of the `render()` method.

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Output, TodoListScreen>() {

  // …

  override fun render(
    renderProps: ListProps,
    renderState: State,
    context: RenderContext
  ): TodoListScreen {
    val titles = renderProps.todos.map { it.title }
    return TodoListScreen(
      username = renderProps.username,
      todoTitles = titles,
      onBackPressed = context.eventHandler("onBackPressed") { setOutput(BackPressed) },
      onRowPressed = context.eventHandler("onRowPressed") { index ->
        // Tell our parent that a todo item was selected.
        setOutput(TodoSelected(index))
      }
    )
  }
}
```

Move the editing actions from the `TodoListWorkflow` to the `TodoNavigationWorkflow`.
We won't be able to call these methods until we can respond to output from the `TodoEditWorkflow`,
but doing this now helps clean up the `TodoListWorkflow`:

```kotlin
object TodoNavigationWorkflow : StatefulWorkflow<TodoProps, State, Output, List<Screen>>() {

  // …

  private fun discardChanges() = action("discardChanges") {
    // To discard edits, just return to the list.
    state = state.copy(step = Step.List)
  }

  private fun saveChanges(
    todo: TodoModel,
    index: Int
  ) = action("saveChanges") {
    // When changes are saved, update the state of that todo item and return to the list.
    state = state.copy(
      todos = state.todos.toMutableList().also { it[index] = todo },
      step = Step.List
    )
  }
}
```

Without any state data, the `initialState` and `snapshotState` functions have no purpose anymore.
It would be nice if we didn't have to write them.

### StatelessWorkflow

Until now, all of our workflows have been subclasses of `StatefulWorkflow`.
When a workflow doesn't have any state data,
it can implement `StatelessWorkflow` instead.
This class only has the render method (with no `renderState` parameter),
and the class only has three type parameters: `PropsT`, `OutputT`, and `RenderT`.

```kotlin
object TodoListWorkflow : StatelessWorkflow<ListProps, Output, TodoListScreen>() {

  // …

  override fun render(
    renderProps: ListProps,
    context: RenderContext
  ): TodoListScreen {
    // …
  }

  // …
}
```

Now that we've simplified the `TodoListWorkflow`,
let's render it and handle its output in the `TodoNavigationWorkflow`:

```kotlin
object TodoNavigationWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Screen>>() {

  // …

  override fun render(
    renderProps: TodoProps,
    renderState: State,
    context: RenderContext
  ): List<Screen> {
    val todoListScreen = context.renderChild(
      TodoListWorkflow,
      props = ListProps(
        username = renderProps.username,
        todos = renderState.todos
      )
    ) { output ->
      when (output) {
        Output.BackPressed -> goBack()
        is TodoSelected -> editTodo(output.index)
      }
    }

    return listOf(todoListScreen)
  }

  private fun goBack() = action("requestExit") {
    setOutput(Back)
  }

  private fun editTodo(index: Int) = action("editTodo") {
    state = state.copy(step = Step.Edit(index))
  }
}
```

So far `RootNavigationWorkflow` is still delegating to the `TodoListWorkflow`.
Update it to delegate to the `TodoNavigationWorkflow` for rendering the `ShowingTodo` state.
This will get us back into a state where we can build again (albeit without editing support):

```kotlin
object RootWorkflow : StatefulWorkflow<Unit, State, Nothing, BackStackScreen<*>>() {

  // …

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): BackStackScreen<*> {

      // …

      is ShowingTodo -> {
        val todoBackStack = context.renderChild(
          child = TodoNavigationWorkflow,
          props = TodoProps(renderState.username),
          handler = {
            // When TodoNavigationWorkflow emits Back, enqueue our log out action.
            logOut
          }
        )
        (listOf(welcomeScreen) + todoBackStack).toBackStackScreen()
      }
    }

    // …
  }
  // …
}
```

#### Moving Edit Output handling to the TodoWorkflow

The `TodoNavigationWorkflow` now can handle the outputs from the `TodoListWorkflow`.
Next, let's add handling for the `TodoEditWorkflow` output events.
Earlier we copied `discardChanges` and `saveChanges` into the `TodoWorkflow`.
We can now call them.

Update the `render` method to show the `TodoEditWorkflow` screen when on the edit step.

```kotlin
object TodoNavigationWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Screen>>() {

  // …

  override fun render(
    renderProps: TodoProps,
    renderState: State,
    context: RenderContext
  ): List<Screen> {
    val todoListScreen = context.renderChild(
      TodoListWorkflow,
      props = ListProps(
        username = renderProps.username,
        todos = renderState.todos
      )
    ) { output ->
      when (output) {
        Output.BackPressed -> goBack()
        is TodoSelected -> editTodo(output.index)
        AddPressed -> createTodo()
      }
    }

    return when (val step = renderState.step) {
      // On the "list" step, return just the list screen.
      Step.List -> listOf(todoListScreen)

      is Step.Edit -> {
        // On the "edit" step, return both the list and edit screens.
        val todoEditScreen = context.renderChild(
          TodoEditWorkflow,
          Props(renderState.todos[step.index])
        ) { output ->
          when (output) {
            DiscardChanges -> discardChanges()
            is SaveChanges -> saveChanges(output.todo, step.index)
          }
        }
        return listOf(todoListScreen, todoEditScreen)
      }
    }
  }

  // …
}
```

That's it! There is now a workflow for both of our current steps of the Todo flow.

## Adding adding

Let's take advantage of our new, cleaner structure by adding one more feature —
it's finally time to make that Add button do something.
Let's work from the bottom up, from `TodoListScreen` through `TodoListWorkflow` to `TodoNavigationWorkflow`.

First give `TodoListScreen` an `onAddPressed` event handler field,
and bind it to `todoListBinding.add`:

```kotlin
data class TodoListScreen(
  val username: String,
  val todoTitles: List<String>,
  val onRowPressed: (Int) -> Unit,
  val onBackPressed: () -> Unit,
  val onAddPressed: () -> Unit
) : AndroidScreen<TodoListScreen> {

  // …

  private fun todoListScreenRunner(
    todoListBinding: TodoListViewBinding
  ): ScreenViewRunner<TodoListScreen> {
    // …
    return ScreenViewRunner { screen: TodoListScreen, _ ->
      // This inner lambda is run on each update.
      todoListBinding.root.setBackHandler(screen.onBackPressed)
      todoListBinding.add.setOnClickListener { screen.onAddPressed() }
    // …
```

Now give `TodoListWorkflow` another `Output` event and post it:

```kotlin
object TodoListWorkflow : StatelessWorkflow<ListProps, Output, TodoListScreen>() {

  // …

  sealed interface Output {
    object BackPressed : Output
    data class TodoSelected(val index: Int) : Output
    object AddPressed : Output
  }

  override fun render(
    renderProps: ListProps,
    context: RenderContext
  ): TodoListScreen {
    // …

      onAddPressed = context.eventHandler("onAddPressed") { setOutput(AddPressed) }
    )
  }
}
```

And make `TodoNavigationWorkflow` honor the new `AddPressed` output,
like the compiler is asking you to.

```kotlin
object TodoNavigationWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Screen>>() {

  // …

  override fun render(
    renderProps: TodoProps,
    renderState: State,
    context: RenderContext
  ): List<Screen> {
    val todoListScreen = context.renderChild(
      TodoListWorkflow,
      props = ListProps(
        username = renderProps.username,
        todos = renderState.todos
      )
    ) { output ->
      when (output) {
        Output.BackPressed -> goBack()
        is TodoSelected -> editTodo(output.index)
        AddPressed -> createTodo()
      }
    }

    // …
  }

  private fun createTodo() = action("createTodo") {
    // Append a new todo model to the end of the list.
    state = state.copy(
      todos = state.todos + TodoModel(
        title = "New Todo",
        note = ""
      )
    )
  }
```

## Conclusion

Is the code better after this refactor?
It's debatable — having the logic in the `TodoListWorkflow` was probably ok for the scope of what the app is doing.
We have had a lot of success honoring this pattern of keeping leaf concerns (individual screens)
separate from navigation concerns right at the outset.
When more screens are added to this flow it will be much easier to reason about,
as there would be a single touchpoint controlling where we are within the subflow of viewing and editing todo items.

Additionally, now the `TodoList` and `TodoEdit` workflows are completely decoupled -
there is no longer a requirement that the `TodoEdit` workflow is displayed after the list.
For instance, we could change the list to have "viewing" or "editing" modes,
where tapping on an item might only allow it to be viewed, but another mode would allow editing.

[Tutorial 5](Tutorial5.md)
