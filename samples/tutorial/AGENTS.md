# Workflow Tutorial Introduction

This is a step by step tutorial for learning how to use Workflow.

## Layout

The project has both a starting point, as well as an example of the completed tutorial.

To help with the setup, we have created a few helper modules:

- `tutorial-views`: A set of 3 views for the 3 screens we will be building, `Welcome`, `TodoList`,
  and `TodoEdit`.
- `tutorial-base`: This is the starting point to build out the tutorial.
- `tutorial-final`: This is an example of the completed tutorial - could be used as a reference if
  you get stuck.

## Tutorial Steps

- [Tutorial 1](Tutorial1.md) - Single view backed by a workflow
- [Tutorial 2](Tutorial2.md) - Multiple views and navigation
- [Tutorial 3](Tutorial3.md) - State throughout a tree of workflows
- [Tutorial 4](Tutorial4.md) - Refactoring
- [Tutorial 5](Tutorial5.md) - Testing

<!-- workflow-kotlin-AGENTS-injection:START -->
# Workflow Kotlin - AI Agent Guide

This is **Square's workflow-kotlin** — a Kotlin multiplatform application framework that provides
unidirectional data flow and state machine primitives for building composable, testable apps.

- Written in Kotlin, targeting JVM and JS
- Used primarily for Android app development (but not Android-specific at the core)
- Repository: https://github.com/square/workflow-kotlin
- Documentation: https://square.github.io/workflow/

## AI Skills Index

This repository includes detailed AI skills with step-by-step guidance for common tasks.
**Read the relevant skill before starting work** — they contain templates, critical rules, and
correct API usage.

| Task | Skill | Location |
|---|---|---|
| Create a new Workflow | `create-workflow` | `workflow-core/.agents/skills/create-workflow/SKILL.md` |
| Write unit tests (testRender) | `workflow-testing` | `workflow-testing/.agents/skills/workflow-testing/SKILL.md` |
| Write integration tests (renderForTest) | `workflow-integration-testing` | `workflow-testing/.agents/skills/workflow-integration-testing/SKILL.md` |

### When to use which skill

- **Creating a workflow?** Read `create-workflow` first — it covers `StatefulWorkflow` vs
  `StatelessWorkflow`, `eventHandler`, Workers, child workflows, and naming conventions.
- **Writing tests for render logic?** Read `workflow-testing` — it covers `testRender`,
  `expectWorkflow`, `expectWorker`, `verifyAction`, and `verifyActionResult`.
- **Writing tests for multi-step flows or async behavior?** Read `workflow-integration-testing` —
  it covers `renderForTest`, `WorkflowTurbine`, `awaitNextRendering`, and `awaitNextOutput`.

## Architecture

| Module | Purpose |
|---|---|
| `workflow-core` | Core types: `StatefulWorkflow`, `StatelessWorkflow`, `Worker`, `WorkflowAction`, `Snapshot` |
| `workflow-runtime` | Runtime engine: `renderWorkflowIn` starts and manages workflow trees |
| `workflow-testing` | Test harnesses: `testRender` (unit), `renderForTest` / `WorkflowTurbine` (integration) |
| `workflow-ui/` | Android UI bindings: `Screen`, `AndroidScreen`, `ComposeScreen`, `BackStackScreen` |
| `workflow-rx2` | RxJava2 interop |
| `workflow-tracing` | Runtime tracing and diagnostics |

## Core Concepts

A **Workflow** is a composable state machine with four type parameters:

- `PropsT` — input from parent (like function arguments). Use `Unit` if none needed.
- `StateT` — internal state (only for `StatefulWorkflow`). Use `Unit` or omit via `StatelessWorkflow`.
- `OutputT` — events emitted to parent. Use `Nothing` if the workflow never emits output.
- `RenderingT` — the "view model" returned each render pass. Typically a `Screen` data class.

Workflows form a **tree**: parents render children via `context.renderChild()`. Props flow down,
outputs flow up, and renderings bubble up to the UI layer.

## Key Patterns

### Workflow Types

**StatefulWorkflow** — has internal state that persists across renders:

```kotlin
object MyWorkflow : StatefulWorkflow<MyProps, MyState, MyOutput, MyScreen>() {
  override fun initialState(props: MyProps, snapshot: Snapshot?): MyState = MyState.Initial
  override fun render(renderProps: MyProps, renderState: MyState, context: RenderContext): MyScreen { ... }
  override fun snapshotState(state: MyState): Snapshot? = null
}
```

**StatelessWorkflow** — rendering is a pure function of props:

```kotlin
object MyWorkflow : StatelessWorkflow<MyProps, MyOutput, MyScreen>() {
  override fun render(renderProps: MyProps, context: RenderContext): MyScreen { ... }
}
```

**Factory functions** for inline definitions:

```kotlin
val myWorkflow = Workflow.stateful<MyProps, MyState, MyOutput, MyScreen>(
  initialState = { props -> MyState.Initial },
  render = { props, state -> MyScreen(...) }
)

val myWorkflow = Workflow.stateless<MyProps, MyOutput, MyScreen> { props -> MyScreen(...) }
```

### Event Handling

**Prefer `eventHandler`** over raw `actionSink.send()` — it provides Compose stability via the
required `name` parameter:

```kotlin
override fun render(renderProps: MyProps, renderState: MyState, context: RenderContext): MyScreen {
  return MyScreen(
    onClicked = context.eventHandler("onClicked") {
      state = renderState.copy(loading = true)
    },
    onItemSelected = context.eventHandler("onItemSelected") { item: Item ->
      setOutput(MyOutput.Selected(item))
    }
  )
}
```

For standalone actions, use `action("name") { ... }`:

```kotlin
private fun loadData() = action("loadData") {
  state = MyState.Loading
}
```

### Async Operations

Use **Workers** for async work. Never perform side effects directly in `render()`.

```kotlin
// Worker from a suspend function
val worker = Worker.from { repository.fetchData() }

// Worker from a Flow
val worker = repository.observeData().asWorker()

// In render():
context.runningWorker(worker, key = "fetchData") { result ->
  action("dataLoaded") { state = MyState.Loaded(result) }
}
```

Use `runningSideEffect` for coroutine-based side effects that don't produce output:

```kotlin
context.runningSideEffect("analytics") {
  analytics.trackScreenView("my_screen")
}
```

### Child Workflows

```kotlin
val childRendering = context.renderChild(ChildWorkflow, props = childProps) { childOutput ->
  // Map child output to parent action
  action("childOutput") { state = handleChildOutput(childOutput) }
}
```

## Code Style

- **Formatting**: `ktlint`
- **Documentation**: KDoc for all public APIs
- **Event handlers**: Always use `eventHandler("descriptiveName")` — the name is required for
  Compose stability and debugging
- **Actions**: Always provide a descriptive name string — `action("whatItDoes") { ... }`
- **Snapshots**: Return `null` from `snapshotState` unless you need persistence

## Testing

### Unit Tests — `testRender` / `RenderTester`

Test individual render passes with faked children and workers. Fast and focused.

```kotlin
workflow.testRender(props = myProps, initialState = myState)
  .expectWorkflow(workflowType = ChildWorkflow::class, rendering = mockRendering)
  .render { rendering ->
    rendering.onClicked()
  }
  .verifyActionResult { newState, output ->
    assertEquals(MyState.Loading, newState)
    assertNull(output)
  }
```

### Integration Tests — `renderForTest` / `WorkflowTurbine`

Test full workflow runtime with real async behavior. Use for multi-step flows.

```kotlin
MyWorkflow.renderForTest {
  val rendering = awaitNextRendering()
  rendering.onClicked()

  val next = awaitNextRendering()
  assertEquals("loading", next.message)
}
```

### Deprecated APIs — Do NOT Use

- ~~`launchForTestingFromStartWith`~~ — use `renderForTest` instead
- ~~`launchForTestingWith`~~ — use `renderForTest` instead
- ~~`WorkflowTestRuntime`~~ — use `WorkflowTurbine` instead

## Common Pitfalls

1. **Don't capture stale state** — In action lambdas, always use the `state` property from the
   `Updater` receiver, never the `renderState` parameter from `render()`.
2. **Don't perform side effects in `render()`** — `render` may be called multiple times for the
   same state. Use `runningWorker` or `runningSideEffect` instead.
3. **Don't use `runBlocking`** — Use coroutines and Workers for async work.
4. **Don't forget `name` on `eventHandler`** — Required for Compose stability and debugging.
5. **Don't emit more than one output per action** — Call `setOutput()` at most once.

## Naming Conventions

- **Workflow**: `[Feature]Workflow` (e.g., `LoginWorkflow`)
- **Props**: `[Feature]Props` or `Unit`
- **State**: `[Feature]State` or nested `data class State` / `sealed interface State`
- **Output**: `[Feature]Output` or `Nothing`
- **Rendering/Screen**: `[Feature]Screen` in a separate file

## Build & Test

```bash
# Build everything
./gradlew build

# Run all JVM tests
./gradlew jvmTest

# Run tests for a specific module
./gradlew :workflow-core:jvmTest
./gradlew :workflow-testing:test

# Check formatting
./gradlew ktlintCheck
```

## Resources

- [Why Workflow](https://square.github.io/workflow/userguide/whyworkflow/)
- [Glossary](https://square.github.io/workflow/glossary/)
- [User Guide](https://square.github.io/workflow/userguide/concepts/)
- [Kotlin Tutorial](samples/tutorial/)
- [Kotlin API Reference](https://square.github.io/workflow/kotlin/api/htmlMultiModule/)
<!-- workflow-kotlin-AGENTS-injection:END -->
