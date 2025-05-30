# Workflow idioms in Compose (WIP)

Here are some recipes for converting common Workflow idioms and patterns to the Compose-based Workflow API proposed in [Compose-based Workflows](compose-based-workflows-design.md).

For brevity, where types, type parameters, or surrounding Workflow subclass pieces are not relevant to an example, they may be omitted.

---

---

## Handling render input (props)

**Workflow**

```kotlin
data class Props(
  val apples: Int,
  val oranges: Int
)

data class State(
  val fruit: Int
)

class MyWorkflow : StatefulWorkflow<Props, State, Output, Rendering>() {
  override fun initialState(props: Props, initialSnapshot): State {
    // This is a contrived example, since this value could just be calculated
    // at render time.
    return State(fruit = props.apples + props.oranges)
  }

  override fun onPropsChanged(old: Props, new: Props, state): State {
    return state.copy(fruit = new.apples + new.oranges)
  }

  override fun render(props: Props, state, context): String {
    return "${props.apples} + ${props.oranges} = ${state.fruit}"
  }
}
```

**Compose**

```kotlin
// Same Props class.
data class Props(
  val apples: Int,
  val oranges: Int
)

@Composable
override fun produceRendering(props: Props, emitOutput): String {
  // Again, very contrived: this simple calculation should just be performed directly
  // in composition.
  var fruit: String by remember { mutableIntStateOf(props.apples + props.oranges) }
  fruit = props.apples + props.oranges

  return "${props.apples} + ${props.oranges} = $fruit"
}
```

## Defining state

### Basic (not saved/restored)

**Workflow**

```kotlin
data class State(
  val someValue: String,
  val valueWithDefault: Int = 0
)

class MyWorkflow : StatefulWorkflow<Props, State, Output, Rendering>() {
  override fun initialState(props, initialSnapshot: Snapshot?): State {
    return State(
      someValue = "initial some value"
    )
  }
}
```

**Compose**

```kotlin
@Composable
override fun produceRendering(props, emitOutput: (String) -> Unit): Rendering {
  // State values are defined inline instead of in a State class.
  var someValue: String by remember {mutableStateOf("initial some value") }
  var valueWithDefault: Int by remember { mutableIntStateOf(0) }
}
```

### Saved and restored via Snapshot

**Workflow**

```kotlin
@Parcelizable
data class State(
  val someValue: String,
  val valueWithDefault: Int = 0
)

class MyWorkflow : StatefulWorkflow<Props, State, Output, Rendering>() {
  override fun initialState(props, initialSnapshot: Snapshot?): State {
    return initialSnapshot?.toParcelable<State>() ?: State(
      someValue = "initial some value"
    )
  }

  override fun snapshotState(state: State): Snapshot? {
    return state.toSnapshot()
  }
}
```

**Compose**

```kotlin
@Composable
override fun produceRendering(props, emitOutput: (String) -> Unit): Rendering {
  // State values are defined inline instead of in a State class.
  // rememberSaveable automatically saves and restores well-known types including primitives.
  var someValue: String by rememberSaveable { mutableStateOf("initial some value") }
  var valueWithDefault: Int by rememberSaveable { mutableIntStateOf(0) }
}
```

## Handling rendering events

**Workflow**

```kotlin
data class State(
  val clickCount: Int = 0,
  val selectedItem: Int = -1
)

data class Rendering(
  val onClick: () -> Unit,
  val onItemSelected: (Int) -> Unit
)

override fun render(props, state, context): Rendering {
  return Rendering(
    onClick = context.eventHandler("onClick") {
      state = state.copy(clickCount = state.clickCount + 1)
      setOutput("Clicked!")
    },
    onItemSelected = context.eventHandler("onItemSelected") { index ->
      state = state.copy(onItemSelected = index)
      setOutput("Item selected: $index")
    }
  )
}
```

**Compose**

```kotlin
// Same Rendering class.
data class Rendering(
  val onClick: () -> Unit,
  val onItemSelected: (Int) -> Unit
)

@Composable
override fun produceRendering(props, emitOutput: (String) -> Unit): Rendering {
  // State values are defined inline instead of in a State class.
  var clickCount: Int by rememberSaveable { mutableIntStateOf(0) }
  var selectedItem: Int by rememberSaveable { mutableIntStateOf(-1) }

  return Rendering(
    onClick = {
      clickCount++
      emitOutput("Clicked!")
    },
    onItemSelected = { index ->
      selectedItem = index
      emitOutput("Item selected: $index")
    }
  )
}
```

## Rendering child workflows

**Workflow**

```kotlin
interface MyChild : Workflow<ChildProps, ChildOutput, ChildRendering>

data class Props(val propsForChild: ChildProps)
data class Output(val outputFromChild: ChildOutput)
data class Rendering(val renderingFromChild: ChildRendering)

class MyWorkflow @Inject constructor(
  private val child: MyChild
) : StatelessWorkflow<Props, Output, Rendering>() {
  override fun render(props, context): Rendering {
    val childRendering = context.renderChild(
      child,
      props = props.propsForChild,
      handler = { childOutput ->
        action { setOutput(Output(childOutput) }
      }
    )

    return Rendering(
      renderingFromChild = childRendering
    )
  }
}
```

**Compose**

```kotlin
interface MyChild : Workflow<ChildProps, ChildOutput, ChildRendering>

data class Props(val propsForChild: ChildProps)
data class Output(val outputFromChild: ChildOutput)
data class Rendering(val renderingFromChild: ChildRendering)

class MyWorkflow @Inject constructor(
  private val child: MyChild
) : ComposeWorkflow<Props, Output, Rendering>() {
  @Composable
  override fun produceRendering(props, emitOutput): Rendering {
    val childRendering = renderWorkflow(
      props = props.propsForChild,
      onOutput = { childOutput ->
        emitOutput(Output(childOutput))
      }
    )

    return Rendering(
      renderingFromChild = childRendering
    )
  }
}
```

## Effects and workers

### Observing lifetime

**Workflow**

```kotlin
TK LifetimeWorker, runningSideEffect
```

**Compose**

```kotlin
TK DisposableEffect, LaunchedEffect
```

### Collecting StateFlow

**Workflow**

```kotlin
class MyWorkflow(
  private val flow: StateFlow<String>
) : StatefulWorkflow<…>() {

  // Should store the worker so you're not calling asUpdateWorker() on
  // every render pass.
  // Must use asUpdateWorker or you'll trigger a second, immediate,
  // redundant render pass.
  private val flowWorker = flow.asUpdateWorker()

  data class State(
    val latest: String
  )

  override fun initialState(props, initialSnapshot): State {
    return State(
      latest = flow.value
    )
  }

  override fun render(props, state, context): Rendering {
    context.runningWorker(flowWorker) { newValue ->
      action {
        state = state.copy(latest = newValue)
      }
    }

    TODO("Do something with ${state.value}")
  }
}

```

**Compose**

```kotlin
class MyWorkflow(
  private val flow: StateFlow<String>
) : ComposeWorkflow<…>() {

  @Composable
  override fun produceRendering(props, emitOutput): Rendering {
    val latest by flow.collectAsState()

    TODO("Do something with $latest")
  }
}
```

### Calling suspend function (not keyed)

**Workflow**

```kotlin
interface Service {
  suspend fun doWork()
}

class MyWorkflow(
  private val service: Service
) : StatefulWorkflow<…>() {

  override fun render(props, state, context): Rendering {
    context.runningSideEffect {
      service.doWork()
    }
  }
}

```

**Compose**

```kotlin
interface Service {
  suspend fun doWork()
}

class MyWorkflow(
  private val service: Service
) : ComposeWorkflow<…>() {

  @Composable
  override fun produceRendering(props, emitOutput): Rendering {
    LaunchedEffect(service) {
      service.doWork()
    }
  }
}
```

### Calling suspend function (keyed)

**Workflow**

```kotlin
interface Service {
  suspend fun doWork(input: Input)
}

data class Input(…)

class MyWorkflow(
  private val service: Service
) : StatefulWorkflow<…>() {

  override fun render(props: Input, state, context): Rendering {
    context.runningSideEffect(props.toString()) {
      service.doWork(props)
    }
  }
}

```

**Compose**

```kotlin
interface Service {
  suspend fun doWork(input: Input)
}

class MyWorkflow(
  private val service: Service
) : ComposeWorkflow<…>() {

  @Composable
  override fun produceRendering(props: Input, emitOutput): Rendering {
    LaunchedEffect(service, props) {
      service.doWork(props)
    }
  }
}
```
