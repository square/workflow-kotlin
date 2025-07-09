# Step 3

_State throughout a tree of workflows_

## Setup

To follow this tutorial, launch Android Studio and open this folder (`samples/tutorial`).

Start from the implementation of `tutorial-2-complete` if you're skipping ahead.

## Editing todo items

Now that a user can "log in" to their todo list, we want to add the ability to edit the todo items listed.

### State ownership

In the workflow framework,
data flows _down_ the tree as properties (`PropsT`) set by parents on their child workflows,
and comes _up_ as output events (`OutputT`) to their parents
(as in the traditional computer science sense that trees that grow downward).

What this means is that state should be created as far down the tree as possible,
to limit the scope of state to be as small as possible.
Additionally, there should be only one "owner" of the state in the tree —
if it's passed farther down the tree,
it should be a copy or read-only version of it —
so there is no shared mutable state in multiple workflows.

When a child workflow has a copy of the state from its parent,
it should change it by emitting an _output_ event back to the parent,
requesting that it be changed.
The child will then receive an updated copy of the data from the parent -
keeping ownership at a single level of the tree.

This is all a bit abstract, so let's make it more concrete by adding an edit todo item workflow.

### Create an edit todo workflow and screen

Using the templates, create a `TodoEditWorkflow` and `TodoEditScreen`.

#### TodoEditScreen

Create an empty `TodoEditScreen` class that will be used as the rendering type for our new workflow
and layout runner.

```kotlin
data class TodoEditScreen(
  // TODO: add properties needed to update TodoEditViewBinding
) : AndroidScreen<TodoEditScreen> {
  override val viewFactory =
    ScreenViewFactory.fromViewBinding(TodoEditViewBinding::inflate, ::todoEditScreenRunner)
}

private fun todoEditScreenRunner(
  binding: TodoEditViewBinding
) = ScreenViewRunner { screen: TodoEditScreen, _ ->
  // TODO
}
```

This view isn't particularly useful without the data to present it.
Update the `TodoEditScreen` to add the needed properties, including event handler callbacks;
and update the view with the data you've added to the screen:

```kotlin
data class TodoEditScreen(
  /** The title of this todo item. */
  val title: TextController,
  /** The contents, or "note" of the todo. */
  val note: TextController,

  val onBackPressed: () -> Unit,
  val onSavePressed: () -> Unit
) : AndroidScreen<TodoEditScreen> {
  override val viewFactory =
    ScreenViewFactory.fromViewBinding(TodoEditViewBinding::inflate, ::todoEditScreenRunner)
}

private fun todoEditScreenRunner(
  binding: TodoEditViewBinding
) = ScreenViewRunner { screen: TodoEditScreen, _ ->
  binding.root.setBackHandler(screen.onBackPressed)
  binding.save.setOnClickListener { screen.onSavePressed() }

  screen.title.control(binding.todoTitle)
  screen.note.control(binding.todoNote)
}
```

Note in particular the use of `TextController` for `title` and `note`,
where you might have been expecting `String`.
`binding.todoTitle` and `binding.todoNote` are both `EditText` instances,
and those things are pretty unwieldy,
especially when you try to drive them from a UDF system.
`TextController` and its `control(EditText)` extension ease that pain.

> [!TIP]
> There is also a `TextController.asMutableTextFieldValueState()` function
> for use with Compose's `BasicTextField` and the like.
> Even with Compose it is tricky to cope with editable text in a declarative way.
>
> It is a truth universally acknowledged,
> that a graphical user interface system in possesion of a good text editing facility,
> must be in want of a decent way to drive it from feature code.

#### TodoEditWorkflow

Now that we have our `Screen`, let's create the `TodoEditWorkflow` to emit it as a rendering.

The `TodoEditWorkflow` needs an initial todo item passed into it from its parent, via `Props`.
It will copy the values from that model to `TextController` fields in its private `State` class, —
which will serve as the "scratch pad" for edits.
This approach allows us to easily discard changes that the user does not choose to save.

```kotlin
object TodoEditWorkflow : StatefulWorkflow<Props, State, Output, TodoEditScreen>() {

  /** @param initialTodo The model passed from our parent to be edited. */
  data class Props(
    val initialTodo: TodoModel
  )

  /**
   * In-flight edits to be applied to the [TodoModel] originally provided
   * by the parent workflow.
   */
  data class State(
    val editedTitle: TextController,
    val editedNote: TextController
  ) {
    /** Transform this edited [State] back to a [TodoModel]. */
    fun toModel(): TodoModel = TodoModel(editedTitle.textValue, editedNote.textValue)

    companion object {
      /** Create a [State] suitable for editing the given [model]. */
      fun forModel(model: TodoModel): State = State(
        editedTitle = TextController(model.title),
        editedNote = TextController(model.note)
      )
    }
  }

  sealed interface Output {
  }

  override fun initialState(
    props: Props,
    snapshot: Snapshot?
  ): State = State.forModel(props.initialTodo)

  // …
```

And let's update the `render` method to return a `TodoEditScreen`,
including event handlers for the Save and Back buttons.
We'll add two `Output` types to report these events to our parent.

```kotlin
object TodoEditWorkflow : StatefulWorkflow<EditProps, State, Output, TodoEditScreen>() {

  // …

  sealed interface Output {
    object DiscardChanges : Output
    data class SaveChanges(val todo: TodoModel) : Output
  }

  // …

  override fun render(
    renderProps: Props,
    renderState: State,
    context: RenderContext
  ): TodoEditScreen = TodoEditScreen(
    title = renderState.editedTitle,
    note = renderState.editedNote,
    onSavePressed = context.eventHandler("onSavePressed") {
      setOutput(SaveChanges(state.toModel()))
    },
    onBackPressed = context.eventHandler("onBackPressed") {
      setOutput(DiscardChanges)
    }
  )

  // …
}
```

## Todo Editing in the full flow

### Updating the current workflows to prepare to add the edit workflow

We want the todo edit screen to be shown when a user taps on an item on the todo list screen.
To do this, we will modify the todo list workflow to show the edit screen when we are editing.

The `TodoListWorkflow` will now occasionally need to render two screens instead of just the one.
Its parent workflow `RootNavigationWorkflow` will add the one or two screens
to the backstack it already constructs.
We need to update `TodoListWorkflow`'s rendering type to `List<Screen>`
so that it can return a list of screens,
instead of just `TodoListScreen`.
The parent workflow needs to know about the list,
so it can pull the screens out and add them to its backstack.

We'll change the rendering type from `TodoListScreen` to `List<Screen>`.
We'll put the existing `todoListScreen` into a list.
We can easily add to this list later.

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, BackPressed, List<Screen>>() {

  // …

  override fun render(
    renderProps: ListProps,
    renderState: State,
    context: RenderContext
  ): List<Screen> {
    val titles = renderState.todos.map { it.title }
    return listOf(
      TodoListScreen(
        username = renderProps.username,
        todoTitles = titles,
        onBackPressed = context.eventHandler("onBackPressed") { setOutput(BackPressed) }
      )
    )
  }

  // …
}
```

Now that `TodoListWorkflow` renders a `List<Screen>`
we need to update the parent `RootNavigationWorkflow` to include this list
in the `BackStackScreen` it renders:

```kotlin
object RootNavigationWorkflow : StatefulWorkflow<Unit, State, Nothing, BackStackScreen<*>>() {

  // ...

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): BackStackScreen<*> {
      // …

      is ShowingTodo -> {
        val todoBackStack = context.renderChild(
          child = TodoListWorkflow,
          props = ListProps(renderState.username),
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

```

Run the app again to validate it still behaves the same.

### Adding the edit workflow as a child to the `TodoListWorkflow`

Now that the `TodoListWorkflow`'s rendering is a list of screens,
it can be updated to show the edit workflow when a `Todo` item is tapped.

Modify the state to represent if the list is being viewed,
or an item is being edited, by adding a new `Step` type:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Back, List<Screen>>() {

  // …

  data class State(
    val todos: List<TodoModel>,
    val step: Step
  ) {
    sealed interface Step {
      /** Showing the list of items. */
      object ShowList : Step

      /**
       * Editing a single item. The state holds the index
       * so it can be updated when a save action is received.
       */
      data class EditItem(val index: Int) : Step
    }
  }

  // …
}
```

Let's add an `onRowPressed` event handler parameter to `TodoListScreen`
and wire it up to the `adapter`:

```kotlin
data class TodoListScreen(
  val username: String,
  val todoTitles: List<String>,
  val onRowPressed: (Int) -> Unit,
  val onBackPressed: () -> Unit,
) : AndroidScreen<TodoListScreen> {

  // …

private fun todoListScreenRunner(
  // …

    adapter.todoList = screen.todoTitles
    adapter.onTodoSelected = screen.onRowPressed
    adapter.notifyDataSetChanged()
  }
}
```

And create an `eventHandler` function to fill it in `TodoListWorkflow.render()`:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Back, List<Screen>>() {

  // …

  override fun render(
    renderProps: ListProps,
    renderState: State,
    context: RenderContext
  ): List<Screen> {
    val titles = renderState.todos.map { it.title }
    return listOf(
      TodoListScreen(
        username = renderProps.username,
        todoTitles = titles,
        onBackPressed = context.eventHandler("onBackPressed") { setOutput(BackPressed) },
        onRowPressed = context.eventHandler("onRowPressed") { index ->
          // When a todo item is selected, edit it.
          state = state.copy(step = Step.EditItem(index))
        }
      )
    )
  }

  // …

}
```

Modify `intialState` to start TodoListWorkflow in `Step.ShowList`:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Back, List<Screen>>() {

  // …

  override fun initialState(
    props: ListProps,
    snapshot: Snapshot?
  ) = State(
    todos = listOf(
      TodoModel(
        title = "Take the cat for a walk",
        note = "Cats really need their outside sunshine time. Don't forget to walk " +
          "Charlie. Hamilton is less excited about the prospect."
      )
    ),
    step = Step.ShowList
  )

```

Add actions for saving or discarding changes:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Back, List<Screen>>() {

  // …

  private fun discardChanges() = action("discardChanges") {
    // Discard changes by simply returning to the list.
    state = state.copy(step = Step.ShowList)
  }

  private fun saveChanges(
    todo: TodoModel,
    index: Int
  ) = action("saveChanges") {
    // To save changes update the state of the item at index and return to the list.
    state = state.copy(
      todos = state.todos.toMutableList().also { it[index] = todo },
      step = Step.ShowList
    )
  }
}
```

Update the `render` method to defer to the `TodoEditWorkflow` when editing,
and to handle the output events it posts:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Back, List<Screen>>() {

  // …

  override fun render(
    renderProps: ListProps,
    renderState: State,
    context: RenderContext
  ): List<Screen> {
    val titles = renderState.todos.map { it.title }
    val todoListScreen = TodoListScreen(
      username = renderProps.username,
      todoTitles = titles,
      onBackPressed = context.eventHandler("onBackPressed") { setOutput(BackPressed) },
      onRowPressed = context.eventHandler("onRowPressed") { index ->
        // When a todo item is selected, edit it.
        state = state.copy(step = Step.EditItem(index))
      }
    )

    return when (val step = renderState.step) {
      // On the "list" step, return just the list screen.
      Step.ShowList -> listOf(todoListScreen)

      // On the "edit" step, return both the list and edit screens.
      is Step.EditItem -> {
        val todoEditScreen = context.renderChild(
          TodoEditWorkflow,
          props = TodoEditWorkflow.Props(renderState.todos[step.index])
        ) { output ->
          when (output) {
            // Send the discardChanges action when the discard output is received.
            DiscardChanges -> discardChanges()

            // Send the saveChanges action when the save output is received.
            is SaveChanges -> saveChanges(output.todo, step.index)
          }
        }

        listOf(todoListScreen, todoEditScreen)
      }
    }
  }

  // …
}
```

Now we have a (nearly) fully formed app! Try it out and see how the data flows between the different workflows:

![Edit-flow](images/full-edit-flow.gif)

### Data Flow

What we just built demonstrates how state should be handled in a tree of workflows:
* The `TodoListWorkflow` is responsible for the state of all the todo items.
* When an item is edited, the `TodoEditWorkflow` makes a _copy_ of it for its local state. The updates happen from the UI events (changing the title or note). Depending on if the user wants to save (hikes are fun!) or discard the changes (taking the cat for a swim is likely a bad idea), it emits an output of `DiscardChanges` or `SaveChanges`.
* When a `SaveChanges` output is emitted, it includes the updated todo model. The parent (`TodoListWorkflow`) updates its internal state for that one item. The child never knows the index of the item being edited, it only has the minimum state of the specific item. This lets the parent be able to safely update its array of todos without being concerned about index-out-of-bounds errors.

If so desired, the `TodoListWorkflow` could have additional checks for saving the changes. For instance, if the todo list was something fetched from a server, it may decide to discard any changes if the list was updated remotely, etc.

## Up Next

We now have a pretty fully formed app. However if we want to keep going and adding features, we may want to reshape our tree of workflows. In the next tutorial, we'll cover refactoring and changing the shape of our workflow hierarchy.

[Tutorial 4](Tutorial4.md)
