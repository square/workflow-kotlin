---
name: workflow-testing
description: Write unit tests for StatefulWorkflow and StatelessWorkflow using testRender and RenderTester. Use for workflow unit testing, render testing, expectWorker, expectWorkflow, action verification, or WorkflowOutput assertions.
---

# Workflow Unit Testing with testRender

Write unit tests for individual render passes using `testRender` and `RenderTester`. This API
fakes all children and workers, letting you test render logic in isolation.

## When to Use

- Testing a single render pass in isolation
- Verifying renderings match expected UI models
- Testing state transitions from event handlers
- Testing output emissions
- Verifying props passed to child workflows
- Verifying worker expectations

For **multi-step flows** or **async behavior**, use `renderForTest` / `WorkflowTurbine` instead
(see the `workflow-integration-testing` skill).

## Test File Structure

```kotlin
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.testing.expectWorker
import com.squareup.workflow1.testing.expectWorkflow
import com.squareup.workflow1.testing.testRender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MyWorkflowTest {

  private val workflow = MyWorkflow()

  @Test fun `renders initial state correctly`() {
    workflow.testRender(
      props = MyProps("test"),
      initialState = MyState.Initial
    )
      .render { rendering ->
        assertEquals("test", rendering.title)
        assertEquals(false, rendering.isLoading)
      }
  }
}
```

## Core API

### Starting a Test

```kotlin
// StatefulWorkflow — provide props and state
workflow.testRender(props = myProps, initialState = myState)

// StatefulWorkflow — use workflow's initialState method (null snapshot)
workflow.testRender(props = myProps)

// StatelessWorkflow — props only
workflow.testRender(props = myProps)
```

### Rendering and Asserting

```kotlin
workflow.testRender(props, state)
  .render { rendering ->
    // Assert on rendering properties
    assertEquals("Hello", rendering.title)
    assertTrue(rendering.isLoading)

    // Optionally trigger an event handler (at most one per test)
    rendering.onButtonClicked()
  }
```

### Verifying Actions

After triggering an event handler or receiving a child/worker output, verify the action:

```kotlin
// Option 1: Verify the action identity (for sealed class / enum actions)
.verifyAction { action ->
  assertEquals(MyAction.LoadData, action)
}

// Option 2: Verify the action result (for inline / anonymous actions)
.verifyActionResult { newState, output ->
  assertEquals(MyState.Loading, newState)
  assertNull(output) // no output emitted
}
```

**`output` is `WorkflowOutput<OutputT>?`** — use `output?.value` to access the actual value,
or check `assertNull(output)` when no output is expected.

## Testing Patterns

### State Transitions

```kotlin
@Test fun `button click transitions to loading`() {
  workflow.testRender(
    props = MyProps("test"),
    initialState = MyState.Initial
  )
    .render { rendering ->
      rendering.onLoadClicked()
    }
    .verifyActionResult { newState, output ->
      assertEquals(MyState.Loading, newState)
      assertNull(output)
    }
}
```

### Output Emissions

```kotlin
@Test fun `complete button emits finished output`() {
  workflow.testRender(
    props = MyProps("test"),
    initialState = MyState.Done("result")
  )
    .render { rendering ->
      rendering.onCompleteClicked()
    }
    .verifyActionResult { newState, output ->
      assertEquals(MyOutput.Finished("result"), output?.value)
    }
}
```

### No Action Triggered

```kotlin
@Test fun `renders loading state without triggering action`() {
  workflow.testRender(
    props = MyProps("test"),
    initialState = MyState.Loading
  )
    .render { rendering ->
      // Just assert, don't trigger any event handler
      assertTrue(rendering.isLoading)
      assertEquals("Loading...", rendering.message)
    }
}
```

## Expecting Child Workflows

All child workflows rendered by the workflow-under-test **must** be faked via providing expectations:

```kotlin
@Test fun `renders child workflow with correct props`() {
  workflow.testRender(props = MyProps("123"), initialState = MyState.ShowChild)
    .expectWorkflow(
      workflowType = ChildWorkflow::class,
      rendering = ChildScreen("faked"),
      key = "",  // default key, omit if not using keys
      assertProps = { props ->
        assertEquals("123", props.itemId)
      }
    )
    .render { rendering ->
      assertEquals("faked", rendering.childContent)
    }
}
```

### Child Workflow Emitting Output

```kotlin
@Test fun `handles child workflow output`() {
  workflow.testRender(props = MyProps("123"), initialState = MyState.ShowChild)
    .expectWorkflow(
      workflowType = ChildWorkflow::class,
      rendering = ChildScreen("faked"),
      output = WorkflowOutput(ChildOutput.Done("result"))
    )
    .render { rendering ->
      // Don't trigger any event handler — child is emitting output
    }
    .verifyActionResult { newState, output ->
      assertEquals(MyState.Complete("result"), newState)
    }
}
```

## Faking Workers

Workers are **optionally** expected by default. Use `requireExplicitWorkerExpectations()` to
make all workers required.

### By Worker Type (KType)

```kotlin
import kotlin.reflect.typeOf

workflow.testRender(props, state)
  .expectWorker(
    workerType = typeOf<Worker<MyData>>(),
    key = "fetchData"
  )
  .render { ... }
```

### By Worker Class (KClass)

```kotlin
workflow.testRender(props, state)
  .expectWorker(
    workerClass = MyCustomWorker::class,
    key = "fetch"
  )
  .render { ... }
```

### By Output Type

```kotlin
workflow.testRender(props, state)
  .expectWorkerOutputting(
    outputType = typeOf<MyData>(),
    key = "fetchData"
  )
  .render { ... }
```

### Worker Emitting Output

```kotlin
workflow.testRender(props, state)
  .expectWorker(
    workerType = typeOf<Worker<MyData>>(),
    key = "fetchData",
    output = WorkflowOutput(MyData("result"))
  )
  .render { rendering ->
    // Don't trigger event handlers — worker is emitting output
  }
  .verifyActionResult { newState, output ->
    assertEquals(MyState.Loaded("result"), newState)
  }
```

## Side Effects

```kotlin
workflow.testRender(props, state)
  .expectSideEffect(key = "analytics")
  .render { ... }
```

Side effects are **optionally** expected by default.
Use `requireExplicitSideEffectExpectations()` to require all side effects be expected.

## Chaining Render Passes

Test multiple sequential renders without the overhead of a full runtime:

```kotlin
@Test fun `multi-step flow`() {
  workflow.testRender(props = MyProps("test"), initialState = MyState.Initial)
    .render { rendering ->
      rendering.onLoadClicked()
    }
    .verifyActionResult { newState, _ ->
      assertEquals(MyState.Loading, newState)
    }
    // Continue with the new state from the previous action
    .testNextRender()
    .expectWorker(workerType = typeOf<Worker<Data>>(), key = "fetch")
    .render { rendering ->
      assertTrue(rendering.isLoading)
    }
}
```

### With New Props

```kotlin
.testNextRenderWithProps(MyProps("updated"))
  .render { rendering ->
    assertEquals("updated", rendering.title)
  }
```

Note: `testNextRenderWithProps` will call `onPropsChanged` if the workflow overrides it.

## Best Practices

1. **Test one behavior per test** — each test should verify one event handler or one render state
2. **Use `verifyActionResult`** for inline/anonymous actions (most common)
3. **Use `verifyAction`** for sealed class or enum actions where you test the action type
4. **Use `requireExplicitWorkerExpectations()`** when you need to verify no unexpected workers run
5. **Descriptive test names** with backticks: `` `button click transitions to loading` ``
6. **Only trigger one event per render** — triggering an event AND having a child/worker emit
   output in the same render is an error

## Required Imports

```kotlin
// Core testing API
import com.squareup.workflow1.testing.testRender

// Expectations
import com.squareup.workflow1.testing.expectWorkflow
import com.squareup.workflow1.testing.expectWorker
import com.squareup.workflow1.testing.expectWorkerOutputting
import com.squareup.workflow1.testing.expectSideEffect

// Output wrapper
import com.squareup.workflow1.WorkflowOutput

// For worker type matching
import kotlin.reflect.typeOf
import com.squareup.workflow1.Worker

// Assertions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
```

## Deprecated APIs — Do NOT Use

- ~~`launchForTestingFromStartWith`~~ — replaced by `renderForTest`
- ~~`launchForTestingWith`~~ — replaced by `renderForTest`
- ~~`WorkflowTestRuntime`~~ — replaced by `WorkflowTurbine`

These are deprecated integration test APIs. For integration testing, use the
`workflow-integration-testing` skill instead.

## Documentation

- RenderTester API: https://square.github.io/workflow/kotlin/api/htmlMultiModule/workflow-testing/com.squareup.workflow1.testing/-render-tester/index.html
- testRender: https://square.github.io/workflow/kotlin/api/htmlMultiModule/workflow-testing/com.squareup.workflow1.testing/test-render.html
- expectWorkflow: https://square.github.io/workflow/kotlin/api/htmlMultiModule/workflow-testing/com.squareup.workflow1.testing/expect-workflow.html
- expectWorker: https://square.github.io/workflow/kotlin/api/htmlMultiModule/workflow-testing/com.squareup.workflow1.testing/expect-worker.html
