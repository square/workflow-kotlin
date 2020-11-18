# Step 4

_Refactoring and rebalancing a tree of Workflows_

## Setup

To follow this tutorial, launch Android Studio and open this folder (`samples/tutorial`).

Start from the implementation of `tutorial-3-complete` if you're skipping ahead.

## Adding new todo items

A gap in the usability of the todo app is that it does not let the user create new todo items. We will add an "add" button on the right side of the navigation bar for this.

## Refactoring a workflow by splitting it into a parent and child

The `TodoListWorkflow` has started to grow and has multiple concerns it's handling — specifically all of the `TutorialListScreen` behavior, as well as the actions that can come from the `TodoEditWorkflow`.

When a single workflow seems to be doing too many things, a common pattern is to extract some of its responsibility into a parent.

### TodoWorkflow

Create a new workflow called `Todo` that will be responsible for both the `TodoListWorkflow` and  the `TodoEditWorkflow`.

```kotlin
object TodoWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Any>>() {

  // …

}
```

#### Moving logic from the TodoList to the TodoWorkflow

Move the `ListState` state, input, and outputs from the `TodoListWorkflow` up to the `TodoWorkflow`. It will be owner the list of todo items, and the `TodoListWorkflow` will simply show whatever is passed into its input:

```kotlin
object TodoWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Any>>() {

  data class TodoProps(val name: String)

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

Define the output events from the `TodoListWorkflow` to describe the `new` item action and selecting a todo item, as well as removing the todo list from the `State`:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, Unit, Output, TodoListScreen>() {

  data class ListProps(
    val name: String,
    val todos: List<TodoModel>
  )

  sealed class Output {
    object Back : Output()
    data class SelectTodo(val index: Int) : Output()
    object NewTodo : Output()
  }

  override fun initialState(
    props: ListProps,
    snapshot: Snapshot?
  ) = Unit

  // …
}
```

TODO add section about converting to StatelessWorkflow.

Change the `WorkflowAction` behaviors to return an output instead of modifying any state:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, Unit, Output, TodoListScreen>() {

  // …

  private fun onBack() = action {
    // When an onBack action is received, emit a Back output.
    setOutput(Back)
  }

  private fun selectTodo(index: Int) = action {
    // Tell our parent that a todo item was selected.
    setOutput(SelectTodo(index))
  }

  private fun new() = action {
    // Tell our parent a new todo item should be created.
    setOutput(NewTodo)
  }
}
```

Update the `render` method to only return the `TodoListScreen`:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, Unit, Output, TodoListScreen>() {

  // …

  override fun render(
    props: ListProps,
    state: Unit,
    context: RenderContext
  ): TodoListScreen {
    val titles = props.todos.map { it.title }
    return TodoListScreen(
        name = props.name,
        todoTitles = titles,
        onTodoSelected = { context.actionSink.send(selectTodo(it)) },
        onBack = { context.actionSink.send(onBack()) }
    )
  }

  // …

}
```

Render the `TodoListWorkflow` and handle its output in the `TodoWorkflow`:

```kotlin
object TodoWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Any>>() {

  // …

  override fun render(
    props: TodoProps,
    state: State,
    context: RenderContext
  ): List<Any> {
    val todoListScreen = context.renderChild(
        TodoListWorkflow,
        props = ListProps(
            name = props.name,
            todos = state.todos
        )
    ) { output ->
      when (output) {
        Output.Back -> onBack()
        is SelectTodo -> editTodo(output.index)
        NewTodo -> newTodo()
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

  private fun newTodo() = action {
    // Append a new todo model to the end of the list.
    state = state.copy(
        todos = state.todos + TodoModel(
            title = "New Todo",
            note = ""
        )
    )
  }
}
```

Update the `RootWorkflow` to defer to the `TodoWorkflow` for rendering the `Todo` state. This will get us back into a state where we can build again (albeit without editing support):

```kotlin
object RootWorkflow : StatefulWorkflow<Unit, State, Nothing, BackStackScreen<*>>() {

  // …

  override fun render(
    props: Unit,
    state: State,
    context: RenderContext
  ): BackStackScreen<*> {

    // Our list of back stack items. Will always include the "WelcomeScreen".
    val backstackScreens = mutableListOf<Any>()

    // Render a child workflow of type WelcomeWorkflow. When renderChild is called, the
    // infrastructure will create a child workflow with state if one is not already running.
    val welcomeScreen = context.renderChild(WelcomeWorkflow) { output ->
      // When WelcomeWorkflow emits LoggedIn, turn it into our login action.
      login(output.name)
    }
    backstackScreens += welcomeScreen

    when (state) {
      // When the state is Welcome, defer to the WelcomeWorkflow.
      is Welcome -> {
        // We always add the welcome screen to the backstack, so this is a no op.
      }

      // When the state is Todo, defer to the TodoListWorkflow.
      is Todo -> {
        val todoListScreens = context.renderChild(TodoWorkflow, TodoProps(state.name)) {
          // When receiving a Back output, treat it as a logout action.
          logout()
        }
        backstackScreens.addAll(todoListScreens)
      }
    }

    // Finally, return the BackStackScreen with a list of BackStackScreen.Items
    return backstackScreens.toBackStackScreen()
  }

  // …

}
```

#### Moving Edit Output handling to the TodoWorkflow

The `TodoWorkflow` now can handle the outputs from the `TodoListWorkflow`. Next, let's add handling for the `TodoEditWorkflow` output events.

Since the types of output and actions are pretty different from their origin, make a *second* set of actions on the `TodoWorkflow`:

```kotlin
object TodoWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Any>>() {

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

Update the `render` method to show the `TodoEditWorkflow` screen when on the edit step:

```kotlin
object TodoWorkflow : StatefulWorkflow<TodoProps, State, Back, List<Any>>() {

  // …

  override fun render(
    props: TodoProps,
    state: State,
    context: RenderContext
  ): List<Any> {
    val todoListScreen = context.renderChild(
        TodoListWorkflow,
        props = ListProps(
            name = props.name,
            todos = state.todos
        )
    ) { output ->
      when (output) {
        Output.Back -> onBack()
        is SelectTodo -> editTodo(output.index)
        NewTodo -> newTodo()
      }
    }

    return when (val step = state.step) {
      // On the "list" step, return just the list screen.
      Step.List -> listOf(todoListScreen)
      is Step.Edit -> {
        // On the "edit" step, return both the list and edit screens.
        val todoEditScreen = context.renderChild(
            TodoEditWorkflow,
            EditProps(state.todos[step.index])
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
