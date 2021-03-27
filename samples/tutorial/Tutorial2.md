# Step 2

_Multiple Screens and Navigation_

## Setup

To follow this tutorial, launch Android Studio and open this folder (`samples/tutorial`).

Start from the implementation of `tutorial-1-complete` if you're skipping ahead.

## Second Workflow

Let's add a second screen and workflow so we have somewhere to land after we log in. Our next screen will be a list of "todo" items, as todo apps are the best apps.

Create a new screen/`LayoutRunner` pair called `TodoList`:

Add the provided `TodoListViewBinding` from `tutorial-views` as a subview to the newly created layout runner:

```kotlin
/**
 * This should contain all data to display in the UI.
 *
 * It should also contain callbacks for any UI events, for example:
 * `val onButtonTapped: () -> Unit`.
 */
data class TodoListScreen(
  val username: String,
  val todoTitles: List<String>,
  val onTodoSelected: (Int) -> Unit,
  val onBack: () -> Unit
)

class TodoListLayoutRunner(
  /** From `todo_list_view.xml`. */
  private val todoListBinding: TodoListViewBinding
) : LayoutRunner<TodoListScreen> {

  private val adapter = TodoListAdapter()

  init {
    todoListBinding.todoList.layoutManager = LinearLayoutManager(todoListBinding.root.context)
    todoListBinding.todoList.adapter = adapter
  }

  override fun showRendering(
    rendering: TodoListScreen,
    viewEnvironment: ViewEnvironment
  ) {
  }

  companion object : ViewFactory<TodoListScreen> by bind(
      TodoListViewBinding::inflate, ::TodoListLayoutRunner
  )
}
```

And then create the corresponding workflow called "TodoList".

Modify the rendering to return a `TodoListScreen`. Modify `State` data class to contain a placeholder parameter, to make the compiler happy. We can leave everything else as the default for now:

```kotlin
object TodoListWorkflow : StatefulWorkflow<Unit, State, Nothing, TodoListScreen>() {

  data class State(val placeholder: String = "")

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = State("initial")

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): TodoListScreen {
    return TodoListScreen(
      username = "",
      todoTitles = emptyList(),
      onTodoSelected = {},
      onBack = {}
    )
  }

  override fun snapshotState(state: State): Snapshot? = null
}
```

### Showing the new screen and workflow

For now, let's just show this new screen instead of the login screen/workflow. Update the activity to show the `TodoListWorkflow`:

```kotlin
// TutorialActivity.kt

private val viewRegistry = ViewRegistry(
    WelcomeLayoutRunner,
    TodoListLayoutRunner
)

class TutorialActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentWorkflow(viewRegistry) {
      Config(TodoListWorkflow, Unit)
    }
  }
}
```

Run the app again, and now the empty todo list (table view) will be shown:

![Empty Todo List](images/empty-todolist.png)

## Populating the Todo List

The empty list is rather boring, so let's fill it in with some sample data for now. Update the `State` type to include a list of todo model objects and change `initialState` to include a default one:

```kotlin
object TodoListWorkflow : StatefulWorkflow<Unit, State, Nothing, TodoListScreen>() {

  data class TodoModel(
    val title: String,
    val note: String
  )

  data class State(
    val todos: List<TodoModel>
  )

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ) = State(
    listOf(
      TodoModel(
        title = "Take the cat for a walk",
        note = "Cats really need their outside sunshine time. Don't forget to walk " +
          "Charlie. Hamilton is less excited about the prospect."
      )
    )
  )

  // …
}
```

Add a `todoTitles` property to the `TodoScreen`, and fill in `showRendering` to update the `TodoListViewBinding` to change what it shows anytime the screen updates:

```kotlin
data class TodoListScreen(
  val todoTitles: List<String>
)

class TodoListLayoutRunner(
  private val todoListBinding: TodoListViewBinding
) : LayoutRunner<TodoListScreen> {

  private val adapter = TodoListAdapter()

  init {
    todoListBinding.todoList.layoutManager = LinearLayoutManager(todoListBinding.root.context)
    todoListBinding.todoList.adapter = adapter
  }

  override fun showRendering(
    rendering: TodoListScreen,
    viewEnvironment: ViewEnvironment
  ) {
    todoListBinding.root.backPressedHandler = rendering.onBack

    with(todoListBinding.todoListWelcome) {
      text = resources.getString(R.string.todo_list_welcome, rendering.username)
    }

    adapter.todoList = rendering.todoTitles
    adapter.notifyDataSetChanged()
  }

  // …
}
```

Finally, update `render` for `TodoListWorkflow` to send the titles of the todo models whenever the screen is updated:

```kotlin
object TodoListWorkflow : StatefulWorkflow<Unit, State, Nothing, TodoListScreen>() {

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): TodoListScreen {
    val titles = renderState.todos.map { it.title }
    return TodoListScreen(
      username = "",
      todoTitles = titles,
      onTodoSelected = {},
      onBack = {}
    )
  }

  // …
}
```

Run the app again, and now there should be a single visible item in the list:

![Todo list hard coded](images/tut2-todolist-example.png)

## Composition and Navigation

Now that there are two different screens, we can make our first workflow showing composition with a single parent and two child workflows. Our `WelcomeWorkflow` and `TodoListWorkflow` will be the leaf nodes with a new workflow as the root.

### Root Workflow

Create a new workflow called `Root` with the templates.

We'll start with the `RootWorkflow` returning a rendering only showing the `WelcomeScreen` via the `WelcomeWorkflow`. Update the `Rendering` type to `Any` and `render()` to return `Any`. This will allow us to eventually render multiple screens each of a different type. Also update `render()` to have the `RootWorkflow` defer to a child workflow:

```kotlin
object RootWorkflow : StatefulWorkflow<Unit, Unit, Nothing, Any>() {

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): Unit = Unit

  override fun render(
    renderProps: Unit,
    renderState: Unit,
    context: RenderContext
  ): Any {
    // Render a child workflow of type WelcomeWorkflow. When renderChild is called, the
    // infrastructure will start a child workflow session with state, if one is not already running.
    val welcomeScreen = context.renderChild(WelcomeWorkflow) { output ->
    }
    return welcomeScreen
  }

  override fun snapshotState(state: State): Snapshot? = null
}
```

However, this won't compile immediately, and the compiler will provide a less than useful error message:

![missing-map-output](images/missing-map-output.png)

Anytime a child workflow is run, the parent needs a way of converting the child's `OutputT` into a `WorkflowAction` the parent can handle. The `WelcomeWorkflow`'s output type is currently a simple object: `object Output`.

For now, delete the `Output` on `WelcomeWorkflow` and replace it with `Nothing`:

```kotlin
object WelcomeWorkflow : StatefulWorkflow<Unit, State, Nothing, WelcomeScreen>() {
  // …
}
```

Update the `TutorialActivity` to start at the `RootWorkflow` and we'll see the welcome screen again:

```kotlin
    setContentWorkflow(viewRegistry) {
      Config(RootWorkflow, Unit)
    }
```

### Navigating between Workflows

Now that there is a root workflow, it can be updated to navigate between the `Welcome` and `TodoList` workflows.

Start by defining the state that needs to be tracked at the root - specifically which screen we're showing, and the actions to login and logout:

```kotlin
object RootWorkflow : StatefulWorkflow<Unit, State, Nothing, WelcomeScreen>() {

  sealed class State {
    object Welcome : State()
    data class Todo(val username: String) : State()
  }

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = Welcome

  // …

  private fun login(username: String) = action {
    state = Todo(username)
  }

  private fun logout() = action {
    state = Welcome
  }
}
```

The root workflow is now modeling our states and actions. Soon we will be able to navigate between the welcome and todo list screens.

### Workflow Output

Workflows can only communicate with each other through their "properties" as inputs and "outputs" as actions. When a child workflow emits an output, the parent workflow will receive it and map it into an action they can handle.

Our welcome workflow has a login button that doesn't do anything, and we'll now handle it and let our parent know that we've "logged in" so it can navigate to another screen.

Add an action for `onLogin` and change our `OutputT` type from `Output` to a new `data class LoggedIn` to be able to message our parent:

```kotlin

object WelcomeWorkflow : StatefulWorkflow<Unit, State, LoggedIn, WelcomeScreen>() {

  data class LoggedIn(val username: String)

  // …

  private fun onUsernameChanged(username: String) = action {
    state = state.copy(username = username)
  }

  private fun onLogin() = action {
    setOutput(LoggedIn(state.username))
  }
}
```

And fire the `onLogin` action any time the login button is pressed:

```kotlin
  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): WelcomeScreen = WelcomeScreen(
      username = renderState.username,
      onUsernameChanged = { context.actionSink.send(onUsernameChanged(it)) },
      onLoginTapped = {
        // Whenever the login button is tapped, emit the onLogin action.
        context.actionSink.send(onLogin())
      }
  )
```

Finally, map the output event from `WelcomeWorkflow` in `RootWorkflow` to the `LoggedIn` action:

```kotlin
  override fun render(
    renderProps: Unit,
    renderState: Unit,
    context: RenderContext
  ): Any {
    // Render a child workflow of type WelcomeWorkflow. When renderChild is called, the
    // infrastructure will create a child workflow with state if one is not already running.
    val welcomeScreen = context.renderChild(WelcomeWorkflow) { output ->
      // When WelcomeWorkflow emits LoggedIn, turn it into our login action.
      login(output.username)
    }
    return welcomeScreen
  }
```

### Showing a different workflow from state

Now we are handling the `LoggedIn` output of `WelcomeWorkflow`, and updating the state to show the `Todo` screen. However, we still need to update our render method to defer to a different workflow.

We'll update the `render` method to show either the `WelcomeWorkflow` or `TodoListWorkflow` depending on the state of `RootWorkflow`

Temporarily define the `OutputT` of `TodoListWorkflow` as `Nothing` (we can only go forward!):

```kotlin
object TodoListWorkflow : StatefulWorkflow<Unit, State, Nothing, TodoListScreen>() {
```

And update the `render` method of the `RootWorkflow`:

```kotlin
object RootWorkflow : StatefulWorkflow<Unit, State, Nothing, Any>() {

  // …

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): Any {
    when (renderState) {
      // When the state is Welcome, defer to the WelcomeWorkflow.
      is Welcome -> {
        // Render a child workflow of type WelcomeWorkflow. When renderChild is called, the
        // infrastructure will create a child workflow with state if one is not already running.
        val welcomeScreen = context.renderChild(WelcomeWorkflow) { output ->
          // When WelcomeWorkflow emits LoggedIn, turn it into our login action.
          login(output.username)
        }
        return welcomeScreen
      }

      // When the state is Todo, defer to the TodoListWorkflow.
      is Todo -> {
        val todoScreen
        = context.renderChild(TodoListWorkflow, Unit)) {
          TODO() // we'll handle output of TodoListWorkflow later
        }
        return todoScreen
      }
    }
  }

  // …
}
```

This works, but with no animation between the two screens it's pretty unsatisfying. We'll fix that by using a different "container" to provide the missing transition animation.

### Workflow Props

So far we are gathering a username from the welcome screen, and there's a place on the todo list screen to display the username, but we're not passing the username from the welcome screen to the list screen. We'll fix this by passing the username down from the `RootWorkflow` to the `TodoListWorkflow` via "props".

Every workflow has a `PropsT` type that allows parents to send information to their children. It's the first parameter in the type parameter list. If a workflow doesn't need any data from its parent, it can use `Unit` as its props type. When rendering a child, a valid props value must always be passed to `renderChild`. The child workflow has access to the props from its parent in a few places:

- `initialState` – the first time the parent renders the child, the props value it provides is passed to this function.
- `onPropsChanged` – this function is only called when the parent passes a different props value to `renderChild` than it did from the last time it called `renderChild`. This method gets both the old and the new props, and can return an updated state to reflect those changes.
- `render` – gets the props the parent passed to `renderChild`. The first time this workflow is rendered, this props will be the same as the value that was passed to `initialState`.
- `WorkflowAction.apply` – when an action is applied, the `Updater` receiver has a reference to the last props used to render the workflow that the action is being applied to. In terms of sequencing, actions always happen _between_ renders.

A workflow's props is similar to its state in a sense: any time the workflow is "alive", it has a current state and a current props. The _state_ is readable and writable, and owned by the workflow – the workflow changes its own state. The _props_ are read-only, and owned by the workflow's parent. The child can't change its props, it can only observe the props its parent decided to pass down.

Note that unlike output, which is how a child sends events to its parent, props does not represent events. In fact, another way to think of props is another kind of state – the "public" part of its state, if you will.

Inside `TodoListWorkflow` create a new `ListProps` class and update TodoListWorkflow to use these props. Also update the method signature of `initialProps` and `render`:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Nothing, TodoListScreen>() {

  data class ListProps(val username: String)

  // …

  override fun initialState(
    props: ListProps,
    snapshot: Snapshot?
  ): State = State(
    // …
  )

  override fun render(
    renderProps: ListProps,
    renderState: State,
    context: RenderContext
  ): TodoListScreen {
    // …
  }
```

Then update `RootWorkflow` to pass `ListProps` when starting `TodoListWorkflow`:

```kotlin
object RootWorkflow : StatefulWorkflow<Unit, State, Nothing, Any>() {

  // …

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): Any {
    when (renderState) {
      // …

      // When the state is Todo, defer to the TodoListWorkflow.
      is Todo -> {
        val todoScreen
        = context.renderChild(TodoListWorkflow, ListProps(username = renderState.username)) {
          TODO() // we'll handle output of TodoListWorkflow later
        }
        return todoScreen
      }
    }
  }
  // …
}
```

### Back Stack and "Containers"

We want to animate changes between our screens. Because we want all of our navigation state to be declarative, we need to use the [`BackStackScreen`](https://square.github.io/workflow/kotlin/api/workflow/com.squareup.workflow1.ui.backstack/-back-stack-screen/) to do this:

```kotlin
class BackStackScreen<StackedT : Any>(
  bottom: StackedT,
  rest: List<StackedT>
) {
  // …

  val frames: List<StackedT> = listOf(bottom) + rest

  // …
}
```

The `BackStackScreen` contains a list of all screens in the back stack that are specified on each render pass. `BackStackScreen` is part of the `workflow-ui-backstack-android` artifact. Update `build.gradle` to include this dependency:

```groovy
dependencies {
  // ...
  implementation deps.workflow.backstack_android
  implementation deps.workflow.core_android
}
```

Update the `RootWorkflow` to return a `BackStackScreen` with a list of back stack items:

```kotlin
object RootWorkflow : StatefulWorkflow<Unit, State, Nothing, BackStackScreen<Any>>() {

  // …

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): BackStackScreen<Any> {

    // Our list of back stack items. Will always include the "WelcomeScreen".
    val backstackScreens = mutableListOf<Any>()

    // Render a child workflow of type WelcomeWorkflow. When renderChild is called, the
    // infrastructure will create a child workflow with state if one is not already running.
    val welcomeScreen = context.renderChild(WelcomeWorkflow) { output ->
      // When WelcomeWorkflow emits LoggedIn, turn it into our login action.
      login(output.username)
    }
    backstackScreens += welcomeScreen

    when (renderState) {
      // When the state is Welcome, defer to the WelcomeWorkflow.
      is Welcome -> {
        // We always add the welcome screen to the backstack, so this is a no op.
      }

      // When the state is Todo, defer to the TodoListWorkflow.
      is Todo -> {
        val todoListScreen = context.renderChild(TodoListWorkflow, props = ListProps(renderState.username)) {
          // When receiving a Back output, treat it as a logout action.
          logout()
        }
        backstackScreens.add(todoListScreen)
      }
    }

    // Finally, return the BackStackScreen with a list of BackStackScreen.Items
    return backstackScreens.toBackStackScreen()
  }

  // …
}
```

We also need to add `BackStackContainer` to the view registry to actually wire up the transition animations:

```kotlin
// TutorialActivity.kt

private val viewRegistry = ViewRegistry(
    BackStackContainer,
    WelcomeLayoutRunner,
    TodoListLayoutRunner
)
```

We also need to update the output type of `TodoListWorkflow`. So far it has output `Nothing`. Define a `Back` class, update `TodoListWorkflow`'s output type, then return this output inside `onBack`:

```kotlin
object TodoListWorkflow : StatefulWorkflow<ListProps, State, Back, TodoListScreen>() {

  // ...

  object Back

  // ...

  override fun render(
    renderProps: ListProps,
    renderState: State,
    context: RenderContext
  ): TodoListScreen {
    val titles = renderState.todos.map { it.title }
    return TodoListScreen(
      username = renderProps.username,
      todoTitles = titles,
      onTodoSelected = {},
      onBack = { context.actionSink.send(onBack()) }
    )
  }

  private fun onBack() = action {
    setOutput(Back)
  }
```

![Welcome to Todo List](images/welcome-to-todolist.gif)

Neat! We can now log in and log out, and show the username entered as our title!

Next, we will add our Todo Editing screen.

[Tutorial 3](Tutorial3.md)
