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

1. **Don't capture stale state** — This is the most common and dangerous pitfall. The `renderState`
   parameter (and any local variable derived from it) is a snapshot from render time. By the time an
   action or eventHandler fires, the real state may have changed. **Always read from `state` on the
   `Updater` receiver inside action/eventHandler lambdas.**

   ```kotlin
   // BAD — direct capture of renderState
   onSave = context.eventHandler("onSave") {
     state = renderState.copy(saving = true)  // STALE!
   }

   // BAD — indirect capture via local variable (easy to miss!)
   val currentName = renderState.name
   val isAdmin = renderState.role == Role.ADMIN
   onSave = context.eventHandler("onSave") {
     state = state.copy(savedName = currentName)  // STALE! currentName came from renderState
   }

   // GOOD — read from `state` on the Updater receiver
   onSave = context.eventHandler("onSave") {
     state = state.copy(saving = true)
   }
   ```

   **With sealed state hierarchies**, use `safeAction` to safely narrow the state type — it no-ops
   if the state has changed to a different subtype by the time the action fires:

   ```kotlin
   private fun onConfirm(item: Item) = safeAction<MyState.Editing>("onConfirm") { editingState ->
     state = MyState.Confirmed(editingState.draft, item)
   }
   ```

   **Rule of thumb**: if a variable was assigned from `renderState` (directly or transitively), it
   must NOT be referenced inside an `action {}` or `eventHandler {}` lambda. Re-derive it from
   `state` inside the lambda instead.

2. **Don't perform side effects in `render()`** — `render` may be called multiple times for the
   same state. Use `runningWorker` or `runningSideEffect` instead.
3. **Don't use `runBlocking`** — Use coroutines and Workers for async work.
4. **Don't forget `name` on `eventHandler`** — Required for Compose stability and debugging.
5. **Don't emit more than one output per action** — Call `setOutput()` at most once.

## Performance Best Practices

> Full details: https://dev-guides.sqprod.co/square/docs/develop/android/performance/best-practices

### Render Rules

- **`render()` must be idempotent** — no long-running operations, disk access, or extensive object
  creation.
- **`snapshotState()` must not serialize** — return a lazy `Snapshot`; serialization happens only
  when needed.
- **One render per action** — populate all state data in `initialState()` to avoid unnecessary
  intermediate render passes.
- **Don't render children to inform parent state** — extract shared logic into helper classes or
  `Scoped` objects accessible to both parent and child.
- **Filter upstream signals at the source** — filter Worker streams before handling, not in the
  output handler, to prevent unnecessary renders.
- **Hoist shared state** — when multiple leaf workflows share state, manage it at the lowest common
  ancestor and pass via props for a single render pass.

### Worker & Action Rules

- Only create Workers when state changes are expected.
- Combine multiple Workers sharing the same source into a single Worker.
- Avoid `Worker<Unit>` except for timers.
- Use `combine()` / `combineTransform()` to consolidate multiple flow lookups into single-pass
  state updates.
- Prefer `SharedFlow` over `StateFlow` for one-time events (avoids automatic re-emission on
  collection).

### eventHandler Rules

- Assign `eventHandler` directly to rendering callbacks only.
- Use only when state changes are intended.
- **Never nest `action()` calls within `eventHandler` lambdas.**

### Compose Stability

- Use stable types in renderings — prefer `ImmutableList` over `List`.
- Don't pass `Lazy` delegates to composable functions.
- Use `RenderContext#remember` to cache expensive view model computations when inputs are unchanged.

### Dependency Injection

- Use `dagger.Lazy<T>` for conditionally-used dependencies to defer expensive instantiation
  (especially useful behind feature flags).
- Use factories only for dependency injection, not stateful object construction — stateful objects
  should be properties for runtime optimization compatibility.

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
