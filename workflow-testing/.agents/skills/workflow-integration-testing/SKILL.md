---
name: workflow-integration-testing
description: Write integration tests for Workflows using renderForTest and WorkflowTurbine. Use when testing full workflow runtime behavior, async operations, state changes over time, output emissions, multi-step user flows, or when user mentions "integration test", "renderForTest", or "WorkflowTurbine".
---

# Workflow Integration Testing with renderForTest

Write integration tests that run a full workflow runtime using `renderForTest` and
`WorkflowTurbine`. Unlike unit tests with `testRender`, these tests execute real workers,
real child workflows, and real async behavior.

## When to Use

| Use `testRender` (unit tests) when... | Use `renderForTest` (integration tests) when... |
|---------------------------------------|---|
| Testing a single render pass          | Testing multi-step user flows |
| Faking all children and workers       | Running real children and workers |
| Verifying render logic in isolation   | Testing async behavior end-to-end |
| Fast, focused tests                   | Testing state changes over time |

## Core API

`renderForTest` is an extension function on `StatefulWorkflow` that:
1. Starts a real workflow runtime
2. Provides a `WorkflowTurbine` for consuming renderings, outputs, and snapshots
3. Automatically manages scope and cleanup when the test block completes

### Basic Pattern

```kotlin
@Test fun `workflow handles user flow`() {
  MyWorkflow.renderForTest {
    // First call returns the initial rendering
    val first = awaitNextRendering()
    assertEquals("Welcome", first.title)

    // Trigger an event on the rendering
    first.onButtonClicked()

    // Await the next rendering after state change
    val second = awaitNextRendering()
    assertEquals("Loading...", second.title)
  }
}
```

## renderForTest Variants

### Unit Props (most common)

```kotlin
// For workflows with Unit props
MyWorkflow.renderForTest {
  val rendering = awaitNextRendering()
  // ...
}
```

### With Props

```kotlin
// For workflows with non-Unit props — pass a StateFlow
MyWorkflow.renderForTest(
  props = MutableStateFlow(MyProps("initial")).asStateFlow()
) {
  val rendering = awaitNextRendering()
  // ...
}
```

### From Specific State

```kotlin
// Start from a particular state (bypasses initialState)
MyWorkflow.renderForTestFromStateWith(
  initialState = MyState.Error("something broke")
) {
  val rendering = awaitNextRendering()
  assertTrue(rendering.isError)
}
```

### From Specific State with Props

```kotlin
MyWorkflow.renderForTestFromStateWith(
  props = MutableStateFlow(MyProps("test")).asStateFlow(),
  initialState = MyState.Loaded("data")
) {
  val rendering = awaitNextRendering()
  // ...
}
```

### For Any Workflow (not just StatefulWorkflow)

```kotlin
// For Workflow<Unit, *, *>
myWorkflow.renderForTestForStartWith {
  val rendering = awaitNextRendering()
  // ...
}

// For Workflow<PropsT, *, *>
myWorkflow.renderForTestForStartWith(
  props = MutableStateFlow(myProps).asStateFlow()
) {
  val rendering = awaitNextRendering()
  // ...
}
```

## WorkflowTurbine API

Inside a `renderForTest` block, you have access to a `WorkflowTurbine` with these methods:

### Renderings

```kotlin
// Await the next rendering (first call returns initial rendering)
val rendering = awaitNextRendering()

// Access the first rendering directly (without consuming it from the turbine)
val first = firstRendering

// Skip N renderings
skipRenderings(3)

// Wait for a rendering that satisfies a predicate
val loaded = awaitNextRenderingSatisfying { it.isLoaded }

// Advanced: filter, map, and assert in one call
val title = awaitNext(
  precondition = { it.isLoaded },
  map = { it.title },
  satisfying = { isNotEmpty() }
)
```

### Outputs

```kotlin
// Await the next output emitted by the workflow
val output = awaitNextOutput()
assertEquals(MyOutput.Completed, output)
```

### Snapshots

```kotlin
// Await the next snapshot
val snapshot = awaitNextSnapshot()

// Access the first snapshot directly
val first = firstSnapshot
```

## Testing Patterns

### Multi-Step User Flow

```kotlin
@Test fun `login flow from welcome to todo list`() {
  RootWorkflow.renderForTest {
    // Start on welcome screen
    val welcome = awaitNextRendering()
    assertEquals("Welcome", welcome.title)

    // Enter name and log in
    welcome.onLogIn("Alice")

    // Should navigate to todo list
    val todoList = awaitNextRendering()
    assertEquals("Alice", todoList.username)
  }
}
```

### Testing Output Emissions

```kotlin
@Test fun `workflow emits output on completion`() {
  MyWorkflow.renderForTest {
    val rendering = awaitNextRendering()
    rendering.onComplete()

    val output = awaitNextOutput()
    assertEquals(MyOutput.Finished, output)
  }
}
```

### Testing Props Changes

```kotlin
@Test fun `workflow responds to prop changes`() {
  val props = MutableStateFlow(MyProps("initial"))

  MyWorkflow.renderForTest(props = props.asStateFlow()) {
    val first = awaitNextRendering()
    assertEquals("initial", first.title)

    // Update props
    props.value = MyProps("updated")

    val second = awaitNextRendering()
    assertEquals("updated", second.title)
  }
}
```

### Starting from Error State

```kotlin
@Test fun `retry from error state`() {
  MyWorkflow.renderForTestFromStateWith(
    initialState = MyState.Error("Network error")
  ) {
    val errorRendering = awaitNextRendering()
    assertTrue(errorRendering.isError)
    assertEquals("Network error", errorRendering.errorMessage)

    // Tap retry
    errorRendering.onRetry()

    val loadingRendering = awaitNextRendering()
    assertTrue(loadingRendering.isLoading)
  }
}
```

### Skipping Intermediate Renderings

```kotlin
@Test fun `final state after multiple transitions`() {
  MyWorkflow.renderForTest {
    val first = awaitNextRendering()
    first.onStart()

    // Skip intermediate loading/progress renderings
    skipRenderings(3)

    // Assert on the final rendering
    val final = awaitNextRendering()
    assertTrue(final.isComplete)
  }
}
```

### Waiting for Specific Rendering

```kotlin
@Test fun `wait for loaded state`() {
  MyWorkflow.renderForTest {
    val first = awaitNextRendering()
    first.onLoadData()

    // Skip all renderings until we get a loaded one
    val loaded = awaitNextRenderingSatisfying { rendering ->
      rendering.isLoaded
    }
    assertEquals("Data loaded", loaded.message)
  }
}
```

## WorkflowTestParams

Customize test behavior with `WorkflowTestParams`:

```kotlin
MyWorkflow.renderForTest(
  testParams = WorkflowTestParams(
    // How to start the workflow
    startFrom = StartFresh,                          // default
    // startFrom = StartFromState(MyState.Loading),  // specific state
    // startFrom = StartFromWorkflowSnapshot(snap),  // from snapshot

    // Check render idempotency (default: true)
    // Calls render() multiple times to detect side effects
    checkRenderIdempotence = true,

    // Override runtime config (default: uses test config)
    runtimeConfig = null
  )
) {
  // ...
}
```

## Configuration

### Custom Timeout

Default timeout is 60 seconds. Override for long-running or time-sensitive tests:

```kotlin
MyWorkflow.renderForTest(
  testTimeout = 10_000L  // 10 seconds
) {
  // ...
}
```

### Custom Coroutine Context

```kotlin
MyWorkflow.renderForTest(
  coroutineContext = StandardTestDispatcher()
) {
  // ...
}
```

### Output Callback

Handle outputs outside the turbine (e.g., for logging):

```kotlin
MyWorkflow.renderForTest(
  onOutput = { output -> println("Got output: $output") }
) {
  // ...
}
```

## Best Practices

1. **Use integration tests for multi-step flows** — unit tests (`testRender`) are better for
   isolated render logic
2. **`awaitNextRendering()` includes the first rendering** — the first call returns the
   synchronously-produced initial rendering
3. **No manual cleanup needed** — `renderForTest` manages scope and cancellation automatically
4. **Don't mix event triggers with child/worker outputs in the same render** — only one source
   of action per render pass
5. **Use `awaitNextRenderingSatisfying`** when intermediate renderings are unpredictable
6. **Use `skipRenderings`** when you know how many intermediate states to skip

## Required Imports

```kotlin
// Core API
import com.squareup.workflow1.testing.renderForTest
import com.squareup.workflow1.testing.renderForTestFromStateWith
import com.squareup.workflow1.testing.renderForTestForStartWith

// Test params (if customizing)
import com.squareup.workflow1.testing.WorkflowTestParams
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFresh
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromState

// For props
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Assertions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
```

## Deprecated APIs — Do NOT Use

- ~~`launchForTestingFromStartWith`~~ — replaced by `renderForTest`
- ~~`launchForTestingWith`~~ — replaced by `renderForTest`
- ~~`launchForTestingFromStateWith`~~ — replaced by `renderForTestFromStateWith`
- ~~`WorkflowTestRuntime`~~ — replaced by `WorkflowTurbine`

## Documentation

- WorkflowTurbine: https://square.github.io/workflow/kotlin/api/htmlMultiModule/workflow-testing/com.squareup.workflow1.testing/-workflow-test-runtime/index.html
- WorkflowTestParams: https://square.github.io/workflow/kotlin/api/htmlMultiModule/workflow-testing/com.squareup.workflow1.testing/-workflow-test-params/index.html
