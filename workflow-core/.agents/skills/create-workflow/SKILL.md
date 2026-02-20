---
name: create-workflow
description: Create Square Workflow classes (StatefulWorkflow or StatelessWorkflow). Use when creating workflows, implementing state machines, handling async operations with Workers, or when user mentions "new workflow", "workflow", "state machine", or "StatefulWorkflow".
---

# Create Workflow

Create properly structured Square Workflow classes following library conventions.

## Quick Start

To create a new workflow, gather:
1. **Workflow name** (e.g., `Login`)
2. **Stateful or Stateless** — does it need internal state?
3. **Props** — input data from parent (`Unit` if none)
4. **Output** — events emitted to parent (`Nothing` if none)
5. **Rendering** — UI model returned each render pass (typically a `Screen` data class)

## Step 1: Choose Workflow Type

**StatefulWorkflow** — use when you need internal state:
- Form data, loading states, error states
- Multi-step flows or navigation
- State that changes in response to events

**StatelessWorkflow** — use when rendering is a direct function of props:
- Simple pass-through or composition of child workflows
- No internal state needed
- Rendering derived entirely from props and child renderings

## Step 2: Define Types

### Props (input from parent)

```kotlin
data class MyWorkflowProps(val userId: String)
// Or use Unit if no props needed
```

### State (StatefulWorkflow only)

State should use any Kotlin types - often a `data class`. If your state needs to be persisted
to disk via a `Snapshot` it needs to be able to be converted to a `ByteString`. 

```kotlin
// Simple state
data class MyState(val name: String, val isLoading: Boolean)

// Sealed state for distinct modes
sealed interface MyState {
  data object Loading : MyState
  data class Loaded(val data: String) : MyState
  data class Error(val message: String) : MyState
}
```

### Output (events to parent)

```kotlin
// Multiple output types
sealed interface MyOutput {
  data object Completed : MyOutput
  data class Selected(val item: Item) : MyOutput
}

// Or use Nothing if no output is ever emitted
```

### Rendering (view model)

Create in a **separate file** named `[Feature]Screen.kt`:

```kotlin
data class MyScreen(
  val title: String,
  val isLoading: Boolean,
  val onAction: () -> Unit,
  val onItemSelected: (Item) -> Unit
) : Screen
```

## Step 3: Create Workflow Class

### StatefulWorkflow — Object Pattern (no dependencies)

Use when the workflow has no injected dependencies:

```kotlin
object MyWorkflow : StatefulWorkflow<MyProps, MyState, MyOutput, MyScreen>() {

  override fun initialState(props: MyProps, snapshot: Snapshot?): MyState =
    MyState.Loading

  override fun render(
    renderProps: MyProps,
    renderState: MyState,
    context: RenderContext
  ): MyScreen {
    return MyScreen(
      title = renderProps.title,
      isLoading = renderState is MyState.Loading,
      onAction = context.eventHandler("onAction") {
        setOutput(MyOutput.Completed)
      },
      onItemSelected = context.eventHandler("onItemSelected") { item: Item ->
        state = MyState.Loaded(item.name)
      }
    )
  }

  override fun snapshotState(state: MyState): Snapshot? = null
}
```

### StatefulWorkflow — Class Pattern (with dependencies)

Use when the workflow needs injected dependencies:

```kotlin
class MyWorkflow(
  private val repository: DataRepository,
) : StatefulWorkflow<MyProps, MyState, MyOutput, MyScreen>() {

  override fun initialState(props: MyProps, snapshot: Snapshot?): MyState =
    MyState.Loading

  override fun render(
    renderProps: MyProps,
    renderState: MyState,
    context: RenderContext
  ): MyScreen {
    // Run a worker for async data loading
    context.runningWorker(
      Worker.from { repository.fetchData(renderProps.id) },
      key = "fetchData-${renderProps.id}"
    ) { result ->
      action("dataLoaded") {
        state = MyState.Loaded(result)
      }
    }

    return MyScreen(
      title = renderProps.title,
      isLoading = renderState is MyState.Loading,
      onAction = context.eventHandler("onAction") {
        setOutput(MyOutput.Completed)
      }
    )
  }

  override fun snapshotState(state: MyState): Snapshot? = null
}
```

### StatelessWorkflow

```kotlin
object MyWorkflow : StatelessWorkflow<MyProps, MyOutput, MyScreen>() {

  override fun render(
    renderProps: MyProps,
    context: RenderContext
  ): MyScreen {
    return MyScreen(
      title = renderProps.title,
      onAction = context.eventHandler("onAction") { action: String ->
        setOutput(MyOutput.ActionSelected(action))
      }
    )
  }
}
```

### Factory Functions (inline definitions)

For simple workflows that don't need a named class:

```kotlin
// Stateful
val counterWorkflow = Workflow.stateful<Unit, Int, Nothing, Int>(
  initialState = 0,
  render = { state ->
    state
  }
)

// Stateless
val greetingWorkflow = Workflow.stateless<String, Nothing, String> { name ->
  "Hello, $name!"
}
```

## Workers (Async Operations)

Use Workers for async work. **Never** perform side effects directly in `render()`.

```kotlin
// Single-value worker from a suspend function
val worker = Worker.from { api.fetchUser(userId) }

// Multi-value worker from a Flow
val worker = repository.observeUpdates().asWorker()

// Custom worker with doesSameWorkAs for deduplication
class FetchWorker(private val id: String) : Worker<Data> {
  override fun run(): Flow<Data> = flow { emit(api.fetch(id)) }
  override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean =
    otherWorker is FetchWorker && otherWorker.id == id
}

// Using a worker in render():
context.runningWorker(worker, key = "fetch") { result ->
  action("fetched") { state = MyState.Loaded(result) }
}
```

## Child Workflows

```kotlin
// Render a child and handle its output
val childScreen = context.renderChild(
  child = ChildWorkflow,
  props = ChildProps(renderProps.itemId)
) { childOutput ->
  action("childOutput") {
    when (childOutput) {
      is ChildOutput.Done -> setOutput(MyOutput.Completed)
      is ChildOutput.Back -> state = MyState.Initial
    }
  }
}

// Child with Nothing output (no handler needed)
val childScreen = context.renderChild(ChildWorkflow, props = childProps)
```

## Side Effects

For coroutine work that doesn't produce workflow output:

```kotlin
context.runningSideEffect("trackScreen") {
  analytics.trackScreenView("my_screen")
}
```

## Critical Rules

1. **Never perform side effects in `render()`** — `render` is called multiple times per state.
   Use `runningWorker` or `runningSideEffect`.
2. **Don't capture `renderState` in lambdas** — `renderState` is a snapshot from render time and
   will be stale when the action fires. This includes **local variables derived from `renderState`**
   (easy to miss!). Always read from `state` on the `Updater` receiver inside action/eventHandler
   lambdas. For sealed state hierarchies, use `safeAction<SpecificState>("name")` which no-ops if
   the state type has changed. See AGENTS.md "Common Pitfalls" for detailed examples.
3. **Always provide `name` to `eventHandler`** — Required for Compose stability and debugging.
4. **Use `setOutput()` to emit output** — Call at most once per action.
5. **Return `null` from `snapshotState`** unless you need state persistence across process death.

## Naming Conventions

| Type | Convention | Example |
|---|---|---|
| Workflow | `[Feature]Workflow` | `LoginWorkflow` |
| Props | `[Feature]Props` or `Unit` | `LoginProps` |
| State | `[Feature]State` or nested type | `LoginState` |
| Output | `[Feature]Output` or `Nothing` | `LoginOutput` |
| Rendering | `[Feature]Screen` (separate file) | `LoginScreen` |

## File Organization

```
feature/
├── MyWorkflow.kt          # Workflow + Props + State + Output
└── MyScreen.kt            # Rendering class (separate file)
```

## Required Imports

```kotlin
// Core workflow types
import com.squareup.workflow1.StatefulWorkflow  // or StatelessWorkflow
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.action

// Workers
import com.squareup.workflow1.Worker
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.asWorker

// UI rendering
import com.squareup.workflow1.ui.Screen

// Factory functions
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.stateful
import com.squareup.workflow1.stateless
```

## Documentation

- Main docs: https://square.github.io/workflow/
- Kotlin API: https://square.github.io/workflow/kotlin/api/htmlMultiModule/
- Tutorial: https://github.com/square/workflow-kotlin/tree/main/samples/tutorial

## Output

When creating a workflow, always:
1. Determine whether StatefulWorkflow or StatelessWorkflow is needed
2. Define Props, State (if stateful), Output, and Rendering types
3. Create the workflow class with proper type parameters
4. Create the Screen rendering in a separate file
5. Use `eventHandler("name")` for UI events
6. Use Workers for async operations
7. Ensure all imports are correct
