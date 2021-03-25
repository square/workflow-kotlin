# Step 4

_Refactoring and rebalancing a tree of Workflows_

## Setup

To follow this tutorial, launch Android Studio and open this folder (`samples/tutorial`).

Start from the implementation of `tutorial-3-complete` if you're skipping ahead.

## Refactoring a workflow by splitting it into a parent and child

The `TodoListWorkflow` has started to grow and has multiple concerns it's handling — specifically all of the `TodoListScreen` behavior, as well as the actions that can come from the `TodoEditWorkflow`.

When a single workflow seems to be doing too many things, a common pattern is to extract some of its responsibility into a parent.

### TodoWorkflow

Create a new workflow called `Todo` that will be responsible for both the `TodoListWorkflow` and the `TodoEditWorkflow`.

```kotlin
object TodoWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Any>>() {

  // …

}
```

#### Moving logic from the TodoListWorkflow to the TodoWorkflow

Move the `ListState` state, input, and outputs from the `TodoListWorkflow` up to the `TodoWorkflow`. It will be owner the list of todo items, and the `TodoListWorkflow` will simply show whatever is passed into its input:

```kotlin
object TodoWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Any>>() {

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

Define the output events from the `TodoListWorkflow` to include back and a new `SelectTodo` output. Also, We no longer need to maintain the todo list in the `State` and we can remove it:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Output, TodoListScreen>() {

  data class ListProps(
    val username: String,
    val todos: List<TodoModel>
  )

  object State

  sealed class Output {
    object Back : Output()
    data class SelectTodo(val index: Int) : Output()
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

  override fun snapshotState(state: Unit): Snapshot? = null

  // …
}
```

Change the `WorkflowAction` behaviors to return an output instead of modifying any state:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Output, TodoListScreen>() {

  // …

  private fun onBack() = action {
    // When an onBack action is received, emit a Back output.
    setOutput(Back)
  }

  private fun selectTodo(index: Int) = action {
    // Tell our parent that a todo item was selected.
    setOutput(SelectTodo(index))
  }
}
```

Move the editing actions from the `TodoListWorkflow` to the `TodoWorkflow`. We won't be able to call these methods until we can respond to output from the `TodoEditWorkflow`, but doing this now helps clean up the `TodoListWorkflow`:

```kotlin
object TodoWorkflow : StatefulWorkflow<TodoProps, State, Output, List<Any>>() {

  // …

  private fun discardChanges() = action {
    // When a discard action is received, return to the list.
    state = state.copy(step = Step.List)
  }

  private fun saveChanges(
    todo: TodoModel,
    index: Int
  ) = action {
    // When changes are saved, update the state of that todo item and return to the list.
    state = state.copy(
        todos = state.todos.toMutableList().also { it[index] = todo },
        step = Step.List
    )
  }
}
```

Because `TodoWorkflow.State` has no properties anymore, it can't be a `data` class, so we need to change it to an `object`. Since the only reason to have a custom type for state is to define the data we want to store, we don't need a custom type anymore so we can just use `Unit`. You might ask why we need a state at all now. We will discuss that in the next section. For now `Unit` will get us moving forward.

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, Unit, Output, TodoListScreen>() {

 override fun initialState(
    props: ListProps,
    snapshot: Snapshot?
  ) = Unit

 override fun render(
    renderProps: ListProps,
    renderState: Unit,
    context: RenderContext
  ): TodoListScreen {
    // …
  }

  override fun snapshotState(state: Unit): Snapshot? = null
}
```

Update the `render` method to only return the `TodoListScreen`:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, Unit, Output, TodoListScreen>() {

  // …

  override fun render(
    renderProps: ListProps,
    renderState: Unit,
    context: RenderContext
  ): TodoListScreen {
    val titles = renderProps.todos.map { it.title }
    return TodoListScreen(
        username = renderProps.username,
        todoTitles = titles,
        onTodoSelected = { context.actionSink.send(selectTodo(it)) },
        onBack = { context.actionSink.send(onBack()) }
    )
  }

  // …

}
```

Without any state data, the `initialState` and `snapshotState` functions have no purpose anymore. It would be nice if we didn't have to write them.

### StatelessWorkflow

Until now, all of our workflows have been subclasses of `StatefulWorkflow`. When a workflow doesn't have any state data, it can implement `StatelessWorkflow` instead. This class only has the render method, the render method has no `state` parameter, and the class only has three type parameters: props, output, and rendering.

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

Now that we've simplified the `TodoListWorkflow`, let's render it and handle its output in the `TodoWorkflow`:

```kotlin
object TodoWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Any>>() {

  // …

  override fun render(
    renderProps: TodoProps,
    renderState: State,
    context: RenderContext
  ): List<Any> {
    val todoListScreen = context.renderChild(
        TodoListWorkflow,
        props = ListProps(
            username = renderProps.username,
            todos = renderState.todos
        )
    ) { output ->
      when (output) {
        Output.Back -> onBack()
        is SelectTodo -> editTodo(output.index)
      }
    }

    return listOf(todoListScreen)
  }

  private fun onBack() = action {
    // When an onBack action is received, emit a Back output.
    setOutput(Back)
  }

  private fun editTodo(index: Int) = action {
    // When a todo item is selected, edit it.
    state = state.copy(step = Step.Edit(index))
  }
}
```

So far `RootWorkflow` is still deferring to the `TodoListWorkflow`. Update the `RootWorkflow` to defer to the `TodoWorkflow` for rendering the `Todo` state. This will get us back into a state where we can build again (albeit without editing support):

```kotlin
object RootWorkflow : StatefulWorkflow<Unit, State, Nothing, BackStackScreen<Any>>() {

  // …

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): BackStackScreen<Any> {

      // …

      // When the state is Todo, defer to the TodoListWorkflow.
      is Todo -> {
        val todoListScreens = context.renderChild(TodoWorkflow, TodoProps(state.username)) {
          // When receiving a Back output, treat it as a logout action.
          logout()
        }
        backstackScreens.addAll(todoListScreens)
      }
    }

    // …
  }
  // …
}
```

#### Moving Edit Output handling to the TodoWorkflow

The `TodoWorkflow` now can handle the outputs from the `TodoListWorkflow`. Next, let's add handling for the `TodoEditWorkflow` output events. Earlier we copied `discardChanges` and `saveChanges` into the `TodoWorkflow`. We can now call them.

Update the `render` method to show the `TodoEditWorkflow` screen when on the edit step. Handle the `TodoEditWorkflow` output by calling `discardChanges` or `saveChanges`.

```kotlin
object TodoWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Any>>() {

  // …

  override fun render(
    renderProps: TodoProps,
    renderState: State,
    context: RenderContext
  ): List<Any> {
    val todoListScreen = context.renderChild(
        TodoListWorkflow,
        props = ListProps(
            username = renderProps.username,
            todos = renderState.todos
        )
    ) { output ->
      when (output) {
        Output.Back -> onBack()
        is SelectTodo -> editTodo(output.index)
      }
    }

    return when (val step = renderState.step) {
      // On the "list" step, return just the list screen.
      Step.List -> listOf(todoListScreen)
      is Step.Edit -> {
        // On the "edit" step, return both the list and edit screens.
        val todoEditScreen = context.renderChild(
            TodoEditWorkflow,
            EditProps(renderState.todos[step.index])
        ) { output ->
          when (output) {
            // Send the discardChanges action when the discard output is received.
            Discard -> discardChanges()
            // Send the saveChanges action when the save output is received.
            is Save -> saveChanges(output.todo, step.index)
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

## Conclusion

Is the code better after this refactor? It's debatable - having the logic in the `TodoListWorkflow` was probably ok for the scope of what the app is doing. However, if more screens are added to this flow it would be much easier to reason about, as there would be a single touchpoint controlling where we are within the subflow of viewing and editing todo items.

Additionally, now the `TodoList` and `TodoEdit` workflows are completely decoupled - there is no longer a requirement that the `TodoEdit` workflow is displayed after the list. For instance, we could change the list to have "viewing" or "editing" modes, where tapping on an item might only allow it to be viewed, but another mode would allow editing.

It comes down to the individual judgement of the developer to decide how a tree of workflows should be shaped - this was intended to provide two examples of how this _could_ be structured, but not specify how it _should_.
