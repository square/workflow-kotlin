# Workflow state/compose brainstorming

~~If end goal is to get to replace Workflow with Compose, then little incremental changes help.~~

~~One such change could be to leverage snapshot state more.~~

Problem: Render passes are a bottleneck for workflows because nothing can consume state outside render passes. If a worker is launched in state A, immediately transitions the workflow to state B, and then in state B a child workflow is rendered which launches its own worker, there’s no way to coalesce these two passes. The child workflow/worker can’t observe state changes from their parent without a render pass.

What if… we just allowed that? In a controlled way, of course.

If a child workflow could consume parent state changes without a render pass, they could happen then in the short term it would be beneficial because the blast radius of a change would *automatically* be localized to the workflows that care, eliminating the need to do an entire render pass. If the second state resulted in more work, with another state change, depending on scheduling that could already eliminate an intermediate render pass.

In the long term, as more state changes are communicated this way, we could get to a point where render passes are entirely separate from state change handling, and get to one-render-per-frame. If we can get to one-render-per-frame, it’s a lot easier to transition the whole runtime to Compose.

The only change we need to make this work is to make the `render` function a restartable function ala https://blog.zachklipp.com/restartable-functions-from-first-principles/. (And maybe the `snapshotState` function too, and maybe `onPropsChanged`?). This would allow a workflow to place state objects in its `StateT` and automatically re-render when those objects are changed if they’re read when rendering. It would also allow other workflows to put state objects in its `PropsT` and to read those and restart appropriately.

Then `runningSideEffect` or workers could use `snapshotFlow` to observe those state objects. We could also provide a variant of `runningSideEffect` that is restartable (`restartingSideEffect`?), like the proposed `TaskEffect` in Compose, and cancels and re-starts its coroutine when a state object changes. This would make running network operations from state or props extremely easy.

```kotlin
val myWorkflow<Props, Output, Rendering> = createWorkflow {
  rendering(initialState = State(this.props)) { context: RenderContext ->
    context.restartingSideEffect("key") {
      val request = Request(this.props)
      val result = request.execute() // suspends
      this.state = State(result)
    }

    Rendering(
      this.props,
      this.state,
      onClick = { state = something }
    )
  }
}
```

```kotlin
fun <P, S, R> createWorkflow(
  block: WorkflowInitScope<P, S, R>.() -> WorkflowRenderingResult<S, R>
): Workflow<P, S, R> = object : AutoWorkflow<P, S, R>() {
  override fun WorkflowInitScope<P, S, R>.defineWorkflow(): WorkflowRenderingResult<S, R> =
    block()
}

interface WorkflowInitScope<P, S, R> {
  val props: P // by mutableStateOf(initialProps)

  /**
   * Ran in a snapshot.
   * Restarted whenever state read inside it changes.
   */
  fun rendering(
    initialState: S,
    block: WorkflowRenderingScope<S, R>.(RenderContext) -> R
  ): WorkflowRenderingResult<S, R> = WorkflowRenderingResult(initialState, block)
}

class WorkflowRenderingResult<S, R> internal constructor(
  internal val initialState: S,
  internal val block: WorkflowRenderingScope<S, R>.() -> R
)

interface WorkflowRenderingScope<S, R> {
  var state: S // by mutableStateOf(initialState)
}
```

Here’s a class version to make it injectable, and 90% of the implementation, assuming the ability for workflows to listen to snapshots is built into the runtime (I think it makes sense to allow every workflow to support that – there’s no API change required):

```kotlin
abstract class AutoWorkflow<P, S, R> : StatefulWorkflow<P, StateHolder<P, S>, Nothing, R>() {
  abstract fun WorkflowInitScope<P, S, R>.defineWorkflow(): WorkflowRenderingResult<S, R>

  final override fun initialState(props: P, snapshot: Snapshot?): StateHolder {
    val initScope = InitScope(initialProps = props)
    val renderingResult = initScope.defineWorkflow()
    return StateHolder(
      initScope = initScope,
      renderingScope = RenderingScope(renderingResult.initialState),
      renderingBlock = renderingResult.block
    )
  }

  // Don't update the props state here, since that would separately invalidate
  // the render method, causing a duplicate invalidation since the runtime
  // guarantees it's going to be called anyway.
  /*
  final override fun onPropsChanged(
    old: P,
    new: P,
    state: StateHolder<P, S>
  ): StateHolder<P, S> {
    // Force sending apply notifications as soon as new props are available.
    Snapshot.withMutableSnapshot {
      state.initScope.props = new
    }
    return state
  }
  */

  final override render(
    renderProps: P,
    renderState: StateHolder<P, S>,
    context: RenderContext
  ): R {
    // This render function will be ran in a mutable snapshot. This will
    // ensure the new props are available to the renderBlock here, and
    // make the new props available to everyone else once this snapshot
    // is applied (after render returns).
    renderState.initScope.props = renderProps
    return renderState.renderBlock(renderState.renderingScope, context)
  }

  private class StateHolder<P, S> (
    val initScope: InitScope,
    val renderingScope: RenderingScope,
    val renderBlock: WorkflowRenderingScope<S, R>.(RenderContext) -> R
  )

  private inner class InitScope(
    initialProps: P
  ) : WorkflowInitScope<P, S, R> {
    override var props: P by mutableStateOf(initialProps)
  }

  private inner class RenderingScope(
    initialState: S
  ) : WorkflowRenderingScope<S, R> {
    override var state: S by mutableStateOf(initialState)
  }
}
```

**What about outputs?**

Don’t need ‘em. Just send a callback through your props.
