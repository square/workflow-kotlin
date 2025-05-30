# Compose-based Workflows

@Zach Klippenstein

## Abstract

In the workflow next initiative, we're considering the value of adapting workflow to take more advantage of compose *on the **presenter** side*. This is completely separate from `workflow-ui`'s support for Compose UI on the view side. See the [Justification](#Justification) section below for why we're considering this. Previous explorations and thought experiments have assumed we would eventually swap out the workflow runtime (`WorkflowNode` et al) with a completely new implementation that uses compose. That would be high risk and very difficult. This is a much more achievable proposal for how we could unblock writing code in the style of Circuit or Cash's presenters in the very near future.

This is an additive, non-breaking change to the current API and runtime and should fully support middle-out-migration (not just top-down or bottom-up). Shouldn't add any performance overhead to the runtime until you actually use the new APIs.

I believe we only need to add two core primitives to the Workflow APIs to support writing presenter code in Compose in a way that complies with all the UDF of Workflow itself:

- Bottom-up: An entry point to call a composable from a `render` method and returns its result. *This can be built without (2), but would be of limited use.*
- Top-down: A special composable that can only be called from a composable from (1) that renders a `Workflow` in a composition and returns its rendering.

On top of these core primitives, we could build more structured features such as:

- A `ComposeWorkflow` type that is like a `Workflow` but instead of `initialState`/`updateProps`/`render` methods it just has a single composable method.

## Teaser

Before getting into the component parts, here's a quick and dirty sketch of the whole picture:

```kotlin
class RootWorkflow(
  // MyWorkflow is defined below – it's a workflow, but compose.
  private val child: MyWorkflow
) : StatelessWorkflow {
  override fun render(props, context): Rendering {
    // Here from a traditional workflow, render a compose one:
    val child1Rendering = context.renderChild(child, childProps, handler = { … })

    // Or even call a composable directly!
    val composableResult = context.renderComposable("key") {
      ComputeSomeResult()
    }
  }
}

// This is a workflow class, but implemented in Compose 🤩
class MyWorkflow(
  // An injected child workflow.
  private val child: Workflow<…>
) : ComposeWorkflow() {
  @Composable
  override fun produceRendering(props: Props): Rendering {
    // do compose stuff! Remember, effect, call other composables, etc.

    // Render a non-compose Workflow:
    renderWorkflow(child, childProps, onOutput = { … })

    return Rendering(…)
  }
}
```

Here's a longer but more realistic sample:

```kotlin
class RootWorkflow @Inject constructor(
  // MyWorkflow is defined below – it's a workflow, but compose.
  private val child: SampleComposeWorkflow
) : StatelessWorkflow {
  override fun render(props, context): Rendering {
    // Here from a traditional workflow, render a compose one:
    context.renderChild(child, childProps, handler = { … })
  }
}

// Just some shared service object, defined somewhere else in the codebase.
interface Service {
  val values: StateFlow<String>
}

data class SampleRendering(
  val label: String,
  val onClick: () -> Unit
)

class SampleComposeWorkflow @Inject constructor(
  private val injectedService: Service,
  private val child: Workflow<String, String, String>
) : ComposeWorkflow<
  /* PropsT */ String,
  /* OutputT */ String,
  /* RenderingT */ SampleRendering
  >() {

  @Composable
  override fun produceRendering(
    props: String,
    emitOutput: (String) -> Unit
  ): SampleRendering {
    // ComposeWorkflows use native compose idioms to manage state, including
    // saving state to be restored later. We don't need to implement the
    // snapshotState method or make aggregate state holders Parcelizable.
    var clickCount by rememberSaveable { mutableIntStateOf(0) }

    // They also use native compose idioms to work with Flows and perform
    // effects. To do this in a traditional workflow this is much more verbose:
    // you'd need to read the flow in initialState and then drop(1) before
    // collecting in the render method.
    val serviceValue by injectedService.values.collectAsState()

    // And they can render child workflows, just like traditional workflows.
    // This child can be any type of Workflow: Stateless, Stateful, Compose, it
    // doesn't matter.
    // This is equivalent to calling BaseRenderContext.renderChild().
    // Note that there's no explicit key: the child key is tied to where it's
    // called in the composition, the same way other composable state is keyed.
    val childRendering = renderWorkflow(
      workflow = child,
      props = "child props",
      // This is equivalent to the handler parameter on renderChild().
      onOutput = {
        myState = ???
        emitOutput("child emitted output: $it")
      }
    )

    return SampleRendering(
      // Reading clickCount and serviceValue here mean that when those values are
      // changed, it will trigger a render pass in the hosting workflow tree,
      // which will recompose this method.
      label = "props=$props, " +
        "clickCount=$clickCount, " +
        "serviceValue=$serviceValue, " +
        "childRendering=$childRendering",
      // Note the lack of rememberLamda: Compose automatically memoizes lambdas
      // created inside a composable, so we get that for free.
      onClick = {
        // Instead of using WorkflowAction's state property, you can just update
        // snapshot state objects directly.
        clickCount++

        // This is equivalent to calling setOutput from a WorkflowAction.
        emitOutput("clicked!")
      }
    )
  }
}
```

## Justification

The purpose of this document is to lay out the technical design for a way to write workflow-layer, presenter code using Composables from inside the existing Workflow architecture and APIs. Fully replacing workflow with something like Circuit is also a potential, if unlikely, long term future but even if we eventually go down that path we would need a migration path, which this design provides. That discussion is out of scope for this document, the author does not intend to argue for or against such a complete migration now or later, merely that *if* we want to allow writing Compose code in Workflow, here's a way to do it.

The idea of taking better advantage of Compose in Workflow is discussed more in the doc [go/workflow-next](https://go/workflow-next). To summarize, Compose ergonomically solves a number of issues, in particular performance issues, that we've been dealing with in Workflow. The Workflow solutions require additional syntax and discipline to use correctly whereas Compose has many of these optimizations built-in and with much cleaner syntax. For more information about performance considerations, see the [Performance](#Performance) section.

In addition, even without considering possible performance improvements, Compose operates on many of the same principles as Workflow but provides a much more ergonomic API. For example, properly collecting a `StateFlow` in a traditional workflow requires doing things in both `initialState` and `render`: reading the initial value and storing in state in `initialState`, and then collecting the flow via a Worker in `render`. The worker must also remember to `drop(1)` to avoid immediately triggering a second render pass when the `StateFlow` immediately emits the item we already read in `initialState`. This is all a single line in Compose:

```kotlin
val state by stateFlow.collectAsState()
```

For more conversion recipes, see:

[Workflow idioms in Compose (WIP)](workflow-idioms-in-compose.md)

## First primitive: Workflow → Compose

This is "top-down" support: Allows a Workflow to run Composable code.

### Workflow → Compose: Sketches

#### Workflow → Compose: Sketches: API

```kotlin
interface BaseRenderContext {
  …

  /**
   * Synchronously composes a [content] function and returns its rendering. Whenever [content] is
   * invalidated, this workflow will be re-rendered and the [content] recomposed to return its new
   * value.
   */
  fun <ChildRenderingT> renderComposable(
    key: String = "",
    content: @WorkflowComposable @Composable () -> ChildRenderingT
  ): ChildRenderingT
}
```

#### Workflow → Compose: Sketches: Usage

```kotlin
class MyWorkflow : StatelessWorkflow {
  override fun render(props, context): Rendering {
    return context.renderComposable(key = "my-composable-key") {
      // rememberSaveable values are saved and restored via the workflow
      // snapshot mechanism.
      var someState by rememberSaveable { mutableStateOf("") }

      MyComposable(
        foo, props.something,
        onEvent = {
          // Outputs are emitted by just sending directly into the action sink.
          context.actionSink.send(action {
            setOutput(…)
          })
        }
      )
    }
  }
}
```

### Workflow → Compose: Implementation

#### Relationship to workflow lifecycles and render passes

The reason this needs to be a first-class method on `BaseRenderContext` is because the Workflow runtime (i.e. `WorkflowNode`) needs to have strict control over the composition's dispatcher and frame clock. When the composition is invalidated it must request a workflow re-render, and the composition must only recompose during a render pass. Compose essentially needs to see a "render pass" as a "frame". This ensures that composables follow the same rules as workflows. It's especially a requirement for Compose → Workflow, because if composables can render child workflows then composition always *must* happen during a parent render pass. When the hosting workflow renders, it would push a frame to the clock and ensure the coroutine dispatcher is allowed to synchronously process the frame.

Compose will already request a frame any time a state value read in the composition is written. We just need to make sure that whenever a frame is requested we mark the workflow as needing re-render. There's no public API to do this now other than sending to the `eventSink`, but I believe we have internal APIs that we can use to wire this up.

When performing a render pass, when a `renderComposable` call is encountered that was also there on a previous render, the workflow runtime needs to drain the coroutine dispatcher to ensure any continuations enqueued have a chance to run before processing the frame. This ensures that any coroutines that update state that needs to invalidate composition have a chance to do so, and is exactly how the dispatcher used in Compose UI works. Then, we check if a frame has been requested. If no frame was requested, it means that the composition hasn't been invalidated since the last render and we don't need to recompose anything.

#### **State saving/restoring**

`rememberSaveable` would be supported by storing data in the workflow's snapshot. This would also require first-class support from `WorkflowNode` since the entries would be declared during the render pass.

#### **Structured concurrency**

The composable's coroutine context (e.g. as used in `LaunchedEffect` and returned from `rememberCoroutineContext()`) would be derived from the `WorkflowNode`'s context to preserve structured concurrency.

### Workflow → Compose: Notes

#### vs Molecule

This API is roughly equivalent to the hosting workflow creating a Molecule Flow to run the compose code, storing it in its state, and collecting the flow with a worker. However, taking control of the frame clock means we can perfectly sync recomposition with render passes. The real benefit of doing our own integration instead of using Molecule comes in the next primitive.

#### `@WorkflowComposable`

The `@WorkflowComposable` annotation on `renderComposable` is a `ComposableTargetMarker` that is used by Compose → Workflow, see below. It also triggers a compiler warning if someone tries to use a UI composable inside a workflow composition (e.g. `renderComposable { MarketLabel() }` is not allowed).

This feature could be implemented first, or even completely on its own without anything else. Namely, it does *not* require Workflow to be able to:

- observe compose state
- render child workflows from composables

## Second primitive: Compose → Workflow

This is the "bottom-up" support: It allows a composable to render a child workflow.

### Compose → Workflow: Sketches

#### Compose → Workflow: Sketches: API

```kotlin
/**
 * Renders a child [Workflow] from any [WorkflowComposable] (e.g. a
 * [ComposeWorkflow.Rendering] or [BaseRenderContext.renderComposable]) and
 * returns its rendering.
 *
 * @param handler An optional function that, if non-null, will be called when
 * the child emits an output. If null, the child's outputs will be ignored.
 */
@WorkflowComposable
@Composable
fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderWorkflow(
  workflow: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
  props: ChildPropsT,
  onOutput: ((ChildOutputT) -> Unit)?
): ChildRenderingT {
  // implementation ommitted
}
```

#### Compose → Workflow: Sketches: Usage

Here's a simple composable that just renders a workflow.

```kotlin
@Composable fun MyComposable(onEvent: (Event) -> Unit): Rendering {
  val child: Workflow<String, Event, Rendering> = …
  return renderWorkflow(child, "props", onOutput = onEvent)
}
```

A more interesting, comprehensive example that ties this in with the First Primitive by making a round-trip from workflow to compose back to workflow. It's essentially an "identity" wrapper, and should be no different than `IdentityWorkflow`'s parent directly rendering `child`. This is not realistic since the inner composable doesn't do anything, but shows how all the pieces wire up:

```kotlin
class IdentityWorkflow(
  private val child: Workflow<Props, Output, Rendering>
) : StatelessWorkflow<Props, Output, Rendering {
  override fun render(props: Props, context: RenderContext): Rendering {
    return context.renderComposable {
      renderWorkflow(child, props, onOutput = { output ->
        context.eventSink.send(action { setOutput(output) })
      })
    }
  }
}
```

### Compose → Workflow: Implementation

#### Return vs emit

The implementation of `renderWorkflow` would probably not be able to use Compose's tree-building `Applier` functionality, since we need the return value immediately in composition. Instead, we would probably supply a composition local from `renderComposable` that would provide a function for `renderWorkflow` to call whose implementation would be provided by the `WorkflowNode` (similar to `ChildRenderer`). However, that's an implementation detail of `renderWorkflow` and we could keep the composition local internal. The pattern of the composition "host" providing the composition a reference to itself through a local is common: e.g. Compose UI provides `LocalView` to access the Android `View` that's hosting the UI, and this mechanism is used to implement many interop features.

#### Re-rendering children

When a child from `renderWorkflow` needs to be rerendered, i.e. its state was changed by an action cascade, we need to make sure we also invalidate the composable that called it. Compose can give us a special handle (`RecomposeScope`) that we can use to explicitly invalidate a recompose scope. We can grab one at each `renderWorkflow` and pass it to the runtime to invalidate the parent composable when the child needs to re-render. This means that when the workflow node that is hosting the composition rerenders, its composition will see there was an invalidation and request a frame.

#### Workflow keys

All child workflows rendered by a composition would end up as children of the workflow hosting the composition (i.e. the one that called `renderComposable`). Each child would be given a key computed from its [composite key](https://developer.android.com/reference/kotlin/androidx/compose/runtime/package-summary?hl=en#currentCompositeKeyHash()) to ensure, with best effort, that each child workflow has a key unique to its position in the composition. Composite key collisions would be resolved using using the `key` composable, like they would for any other compose key collisions. This key would also be used to restore child workflows from their snapshots—state saving/restoration is the main use case for `currentCompositeKeyHash` in the workflow runtime as well.

Additionally, every call to `renderWorkflow` probably should internally key its entire state off of the workflow instance as well.

#### Structured concurrency

One difference with child workflows rendered via `renderWorkflow` is that they would need to take their parent coroutine `Job` from the composition's coroutine context instead of using the parent `WorkflowNode`'s. This preserves structured concurrency, e.g. when a child workflow stops being rendered because a composable goes away.

#### Event sinks

We might need to think harder about `WorkflowAction`s and the `eventSink`: Since that's the only way to propagate outputs up the chain, for external (read: rendering) events, the default behavior is fine (enqueuing actions in the host node's channel). However, when a child workflow's output handler (see Second Primitive below) is called, then it is called as part of an action cascade, which means we need to synchronously produce the `WorkflowAction` when the node that initiated the cascade is processing them.

I would really rather not introduce some awkward API to force all compose-originated events to *return* actions, since that pattern would need to bubble all the way up through event handlers, and it's kind of awkward. And the state part of the action is meaningless inside compose.

To adapt this, we could introduce a `ThreadLocal` that we set when calling `onOutput` that causes `eventSink.send` to just pass any actions out to the calling stack frame instead of enqueuing to the channel. We would need a `ThreadLocal` since there might be chained callbacks up multiple composables. Then when `renderWorkflow` invokes the `onOutput` callback from the child workflow's `Output -> WorkflowAction` handler, it uses this mechanism to collect all the actions emitted by callbacks. It can then return the first action from the handler, ensuring it's processed as part of the same cascade. Most callbacks should never emit more than one output per invocation, but if it did, they could just be enqueued like they would be otherwise.

### Compose → Workflow: Notes

#### Naming

Most composables start with an uppercase letter. `renderWorkflow` does not because it returns a value. Compose API guidelines say that composables that emit a value should be named as a noun and capitalized like a class, but composables that return values should be named as verbs and capitalized like normal functions.

#### Receiver vs param

The `renderWorkflow` composable has no receiver because it's more idiomatic for composables to be top-level functions (and some consider that a best practice even when there's a need for them to have a receiver anyway).

#### Composition type safety

The `@WorkflowComposable` target marker annotation is what informs the compose compiler that `renderWorkflow` is a special "type" of composable that can only be called in the context of a `renderComposable` and can't be called in other types of compositions like UI composables. Doing so will cause a compiler warning (not an error, since this feature was introduced after Compose 1.0, but it may become an error in the future). Since other types of compositions would not have the composition local required to render children, it would also throw a runtime exception, but the annotation allows us to catch that kind of mistake at compile time.

#### Multiple child renders

Since a single composable can potentially recompose multiple times in the same frame (e.g. due to a backwards write), the workflow runtime would need to support rendering a child multiple times in a single parent render pass. This is not possible to do right now, but I don't believe there are any technical limitations in the runtime that would prevent us from doing it.

## Capstone: `ComposeWorkflow`

This component is built entirely on the first primitive above, and can use the second primitive. It merely an extension to the above and does not require any further changes to the workflow runtime.

### Sketches

#### ComposeWorkflow  API

```kotlin
/**
 * A [Workflow]-like interface that participates in a workflow tree via its
 * [Rendering] composable.
 */
@Stable
public abstract class ComposeWorkflow<
  in PropsT,
  out OutputT,
  out RenderingT
  > : Workflow<PropsT, OutputT, RenderingT> {

  /**
   * The main composable of this workflow that consumes some [props] from its
   * parent and may emit an output via [emitOutput].
   *
   * Equivalent to [StatefulWorkflow.render].
   */
  @WorkflowComposable
  @Composable
  protected abstract fun produceRendering(
    props: PropsT,
    emitOutput: (OutputT) -> Unit
  ): RenderingT
}
```

#### ComposeWorkflow Implementation

To implement the `Workflow` interface, we need to have a function that returns a `StatefulWorkflow` with the actual implementation. That's trivial: we just return a really simple workflow that does nothing but call `renderComposable` from above in its render method:

```kotlin
  private inner class ComposeWorkflowWrapper :
    StatefulWorkflow<PropsT, Unit, OutputT, RenderingT>() {

    override fun initialState(
      props: PropsT,
      snapshot: Snapshot?
    ) {
      // Noop
    }

    override fun render(
      renderProps: PropsT,
      renderState: Unit,
      context: RenderContext
    ): RenderingT = context.renderComposable {
      // Explicitly remember the output function since we know that actionSink
      // is stable even though Compose might not know that.
      val emitOutput: (OutputT) -> Unit = remember(context.actionSink) {
        { output -> context.actionSink.send(OutputAction(output)) }
      }

      return@renderComposable produceRendering(
        props = renderProps,
        emitOutput = emitOutput
      )
    }

    override fun snapshotState(state: Unit): Snapshot? = null

    private inner class OutputAction(
      private val output: OutputT
    ) : WorkflowAction<PropsT, Unit, OutputT>() {
      override fun Updater.apply() {
        setOutput(output)
      }
    }
  }
```

We can also add a special case to the `renderWorkflow` composable to compose `ComposeWorkflow`s directly, without going out into the workflow runtime:

```kotlin
@WorkflowComposable
@Composable
fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderWorkflow(
  workflow: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
  props: ChildPropsT,
  onOutput: ((ChildOutputT) -> Unit)?
): ChildRenderingT =
  if (workflow is ComposeWorkflow) {
    // This method is explained below.
    workflow.renderWithRecomposeBoundary(props, onOutput)
  } else {
    val host = LocalWorkflowCompositionHost.current
    host.renderChild(workflow, props, onOutput)
  }
```

#### Optional: Recompose scope isolation

When Compose needs to recompose something, it recomposes a particular "recompose scope". Roughly, each composable is a recompose scope. However, there are two exceptions to this rule: both inline functions and *any function that returns non-Unit* are not given their own scopes and instead will just ask their parent to recompose instead. Since all workflow-y composables return a value, none of them are recompose scopes, which means that any time anything in the composition changes we'd basically invalidate the entire composition tree. To avoid this, `renderWithRecomposeBoundary` does a little trickery to ensure that every `renderWorkflow` call can recompose below without recomposing above. We can define it as an internal method on `ComposeWorkflow` which also has the benefit of allowing the abstract `produceRendering` to be `protected`.

```kotlin
abstract class ComposeWorkflow … {
  …

  @Composable
  internal fun renderWithRecomposeBoundary(
    props: PropsT,
    onOutput: ((OutputT) -> Unit)?
  ): RenderingT {
    // Since this function returns a value, it can't restart without also restarting its parent.
    // IsolateRecomposeScope allows the subtree to restart and only restarts us if the rendering
    // value actually changed.
    val renderingState = remember { mutableStateOf<RenderingT?>(null) }
    RecomposeScopeIsolator(
      props = props,
      onOutput = onOutput,
      result = renderingState
    )

    // The value is guaranteed to have been set at least once by RecomposeScopeIsolator so this cast
    // will never fail. Note we can't use !! since RenderingT itself might nullable, so null is
    // still a potentially valid rendering value.
    @Suppress("UNCHECKED_CAST")
    return renderingState.value as RenderingT
  }

  /**
   * Creates an isolated recompose scope that separates a non-restartable caller ([render]) from
   * a non-restartable function call ([produceRendering]). This is accomplished simply by this
   * function having a [Unit] return type and being not inline.
   *
   * **It MUST have a [Unit] return type to do its job.**
   */
  @Composable
  private fun RecomposeScopeIsolator(
    props: PropsT,
    onOutput: ((OutputT) -> Unit)?,
    result: MutableState<RenderingT?>,
  ) {
    result.value = produceRendering(props, onOutput ?: {})
  }
}
```

Making all these `MutableState`s is not free either though, so we should benchmark to figure out whether it's worth isolating scopes or just let the whole thing recompose. Every other "framework" approach to this style of presentation-level composables that I've seen (Cash App, Circuit) just return values directly and don't bother with the isolation, and I suspect that if there is a speed benefit we wouldn't even notice it until we have a giant composable tree. It might be simpler to leave it out for v1, we can add it back without any API changes.

### Notes

It's very possible to build real features on just the first two primitive within an existing workflow codebase. There's nothing stopping anyone from just writing things as pure composables and calling them simply as composables.

Having a dedicated `Workflow` type for compose workflows is useful for two main reasons:

- As it concerns other code trying to call this workflow: It allows new, Compose-based code to be plugged directly into traditional workflows, without the consumer knowing or caring that this happens to be implemented using Compose. This is essentially an adapter layer from Compose to traditional workflows.
- As it concerns the implementer of this workflow: It allows the composable to be injected from Dagger via the class. [New work being done on Dagger](https://docs.google.com/document/d/1DwykcYKWTUnASz3tWIaDjdWUGiZ8W6WakCuFrwnyNB0/edit) (by @Jacob Applin) and other dependency injection frameworks ([kotlin-inject](https://github.com/evant/kotlin-inject#function-injection), [Metro](https://zacsweers.github.io/metro/injection-types/#top-level-function-injection)) support directly injecting top-level functions, which makes this point less interesting in the long term: Traditional workflows might still want to inject a `Workflow` instance, but composables could just inject other composables and call them directly.

## General notes

### Dependencies

This proposal adds Compose support as a first-class citizen to workflow. This means that the core `workflow-runtime` module would need to bring in an API dependency on `compose-runtime`. It would *not* need to depend on any Compose UI stuff. We've historically avoided any Compose dependencies from this module, but I believe that if we are serious about this sort of major feature, this is acceptable.

We need to depend on Compose multiplatform though, which is always a few versions behind Android-only Compose, so I'm not sure how much a wrench this is going to through into our version management. As an aside, I really wish we'd never made Workflow multiplatform; I don't see the value it gives us matching the trouble.

### Propagating composition locals

Workflows are hierarchical, and compositions are hierarchical. Compositions share data with their children through composition locals. Subcompositions are created like normal compositions, but by also passing the parent's `CompositionContext` when creating the composition. This way, the child can see any composition locals provided by the parent. I think we should support this functionality, so that in a structure `WorkflowA → CompositionA → WorkflowB → CompositionB`, `CompositionB` sees any composition locals provided by `CompositionA`.

Update: This might be impossible, since each workflow composition *host* needs to be able to control `Recomposer` manually, and when creating a composition you can only specify one `CompositionContext` – either a parent, or a root `Recomposer`, but not both. We effectively need to make a bunch of different _root_ compositions, not subcompositions of a single root. This is different from how Compose UI embeds compositions in a View hierarchy, where every nested composition is a subcomposition and they all share the same `Recomposer` and frame clock.

### Tracing

Workflow supports tracing instrumentation, allowing us to surface every workflow's re-render in Perfetto traces. Compose has similar functionality to show recompositions. When both these traces are enabled, recompositions and workflow renders should show as nested calls in traces without us having to do anything special. This works because this approach performs recomposition synchronously during a render pass and so the compose calls actually happen during the hosting render stack frame.

### Workflow interceptors

The `WorkflowInterceptor` API is built around `StatefulWorkflow` and, in its current form, would not have visibility into:

- `renderComposable`
- `renderWorkflow` when passed a child `ComposeWorkflow` (since it would be composed directly and not go through the workflow runtime).
- `renderWorkflow` when passed a traditional child workflow. It would technically see the render, but it would only see it as a render of the workflow hosting the composable rendering the child, it wouldn't see any of the compose stuff in between.

We can explore extending the interceptor API to support `renderComposable` and `ComposeWorkflow` → `ComposeWorkflow` boundaries, but it is impossible to support intercepting arbitrary composables.

### Performance

#### Expected wins

Many of the optimizations we've identified are necessary to make Workflow perform acceptably were already shipping in Compose since 1.0.

| Workflow feature | Compose feature |
| --- | --- |
| Render only when state changes | Compose only invalidates recompose scopes (basically, composables) when state objects they've read are written. |
| Conflate stale renderings | Compose does not do this by default, since it's primarily designed around composables *emitting* and not *returning*. However, `renderComposable` will conflate renderings at the workflow-compose boundary. We also may be able to use some tricks to conflate renderings inside a single composition, see [Recompose scope isolation](#optional-recompose-scope-isolation). |
| Partial tree rendering | Compose calls this “skipping” and will avoid recomposing composables when their internal state hasn't changed and their caller passes the same parameters. |
| Stable lambdas and `eventHandler` memoization | Compose automatically memoizes certain lambdas created during composition, so composables can pass callbacks around that are just plain undecorated lambdas and we don't have to worry about them breaking skipping in most cases. |

#### Known risks

#### Many compositions

The biggest performance risk is that at some point we end up with a ton of `renderComposable` calls all over the workflow tree. Each `renderComposable` creates a separate compose runtime (`Recomposer` and `Composition`, and maybe a `Dispatcher` although we might be able to share a dispatcher across the whole workflow runtime). Most of this cost is paid on the first render, but a deep workflow tree with many `renderComposable` calls may be slow.

#### Recompose scopes

Composables that return a value do not form a recomposition scope and will invalidate their parent when they need to recompose. Since our approach (like others, see below) is based on composables that return values instead of emit, we miss out on some Compose optimizations by default. Since every other known use of composables-as-presenters should have this same problem, and it has not come up in any publications from other companies using this approach, it might not be something we have to worry about. Depending how much of a cost this is, we may be able to mitigate using the [Recompose scope isolation](#optional-recompose-scope-isolation) trick, although there does not seem to be any other large-scale use of that pattern and we need to do a lot of benchmarking and profiling to see how it actually performs.

## Comparison to Compose UI

Each initial call to `renderComposable` (the first call in a given workflow with a given key) is roughly equivalent in order of magnitude cost to attaching a `ComposeView` to a `View`. Each subsequent re-render of that `renderComposable` is much cheaper, comparable to the work Compose UI does to recompose on a non-initial frame. The exact times will be different since Workflow has different (and should be quite a bit fewer) things to integrate with. Composables inside a workflow tree also don't have measure or draw phases, only composition.

## Comparison to similar solutions

### Molecule (Cash App)

Cash App uses Compose for presenters already, and has been for some time. They write presenters as composable functions that return view model objects.

To support this without using Compose UI, they built a "pure" coroutine-based runtime for hosting compositions that don't emit called [Molecule](https://code.cash.app/bridge-between-your-code-and-compose). Contrary to popular belief, Molecule is not an architecture but just an alternative runtime. You could theoretically a tree of Compose presenters and compose it directly in a UI composition as well. Molecule is especially helpful for testing non-UI composables.

The design in this document allows writing presenters in the same way as Cash, but the runtime is a little different. The entry point to molecule is a function that takes a composable and returns a `Flow` or `StateFlow` of the return values of that composable. It's up to the caller to collect the flow. From the blog linked above:

```kotlin
/**
 * Create a [Flow] which will continually recompose `body` to produce a stream of [T] values
 * when collected.
 */
fun <T> moleculeFlow(
  clock: RecompositionClock,
  body: @Composable () -> T,
): Flow<T>

/**
 * Launch a coroutine into this [CoroutineScope] which will continually recompose `body`
 * to produce a [StateFlow] stream of [T] values.
 */
fun <T> CoroutineScope.launchMolecule(
  clock: RecompositionClock,
  body: @Composable () -> T,
): StateFlow<T>
```

The design in this document has a different entry point: `renderComposable`. It is called from a render pass and always returns a single value. Instead of emitting subsequent values when the composable recomposes through a `Flow`, we sync the recomposition to the render pass and just trigger another render pass, which will recompose and return the new value from `renderComposable`. We need this tight synchronization so that composables can render workflows themselves. If we didn't need that, we could just call `moleculeFlow`, turn the returned flow into a Worker, and run the worker. Every time a composition is invalidated it would trigger its own re-render. The approach in this document performs composition inline, during the actual render pass, which allows multiple compositions to be recomposed in the same render pass, and will also show compose tracing sections nested inside workflow render traces.

In terms of overhead, each call to `renderComposable` is roughly equivalent to a `launchMolecule` call on the initial render pass. Subsequent re-renders of the same `renderComposable` call (in the same workflow with the same key) are much cheaper.

### Molecule Presenters (Cash App)

Cash builds a layer on top of Compose for their presenters.

_internal, redacted_

### Circuit (Slack)

[Circuit](https://slackhq.github.io/circuit/) is a library built by Slack that, unlike Molecule, actually *is* an architecture framework much like Workflow. Basically it's Workflow, completely rewritten from scratch to use Compose fully. It also has different opinions about navigation which POS Foundation tends to disagree with based on previous experience with similar navigation approaches. The core concept is the same as the approach in this document though: Presenters are just composables that return values.

### Trio (Airbnb)

TK

## Other links

- Draft PR with a rough code sketch: https://github.com/square/workflow-kotlin/pull/1268
- [Less ambitious proposal](workflow-state-compose-brainstorming.md) to merely make render methods aware of snapshot state
- [Workflow idioms in Compose (WIP)](workflow-idioms-in-compose.md)
