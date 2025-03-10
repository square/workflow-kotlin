package com.squareup.workflow1

import androidx.compose.runtime.Composable
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Provides hooks into the workflow runtime that can be used to instrument or modify the behavior
 * of workflows.
 *
 * This interface's methods mirror the methods of [StatefulWorkflow]. It also has one additional
 * method, [onSessionStarted], that is notified when a workflow is started. Each method returns the
 * same thing as the corresponding method on [StatefulWorkflow], and receives the same parameters
 * as well as two extra parameters:
 *
 *  - **`proceed`** – A function that _exactly_ mirrors the corresponding function on
 *    [StatefulWorkflow], accepting the same parameters and returning the same thing. An interceptor
 *    can call this function to run the actual workflow, but it may also decide to not call it at
 *    all, or call it multiple times.
 *  - **`session`** – A [WorkflowSession] object that can be queried for information about the
 *    workflow being intercepted. Note that this object carries [parent][WorkflowSession.parent]
 *    information. So we can use the session object to determine if we are the root Workflow if
 *    session.parent == null.
 *
 * All methods have default no-op implementations.
 *
 * ## On Profiling
 *
 * Note that the [WorkflowInterceptor]'s methods will call the actual methods with the proceed
 * function. This means that we have hooks before and after the actual method making it very
 * straightforward to trace/measure the timing of any part of any workflow, or of the whole
 * tree.
 *
 * ## Workflow sessions
 *
 * A single workflow may be rendered by different parents at the same time, or the same parent at
 * different, disjoint times. Each continuous sequence of renderings of a particular workflow type,
 * with the same key passed to [BaseRenderContext.renderChild], is called a "session" of that
 * workflow. The workflow's [StatefulWorkflow.initialState] method will be called at the start of
 * the session, and its state will be maintained by the runtime until the session is finished.
 * Each session is identified by the [WorkflowSession] object passed into the corresponding method
 * in a [WorkflowInterceptor].
 *
 * In addition to the [WorkflowIdentifier] of the type of the workflow being rendered, this object
 * also knows the [key][WorkflowSession.renderKey] used to render the workflow and the
 * [WorkflowSession] of the [parent][WorkflowSession.parent] workflow that is rendering it.
 *
 * Each session is also assigned a numerical ID that uniquely identifies the session over the
 * life of the entire runtime. This value will remain constant as long as the workflow's parent is
 * rendering it, and then it will never be used again. If this workflow stops being rendered, and
 * then starts again, the value will be different.
 */
public interface WorkflowInterceptor {

  /**
   * Called when the session is starting, before [onInitialState].
   *
   * @param workflowScope The [CoroutineScope] that will be used for any side effects the workflow
   * runs, as well as the parent for any workflows it renders.
   */
  public fun onSessionStarted(
    workflowScope: CoroutineScope,
    session: WorkflowSession
  ): Unit = Unit

  /**
   * Intercepts calls to [StatefulWorkflow.initialState].
   */
  public fun <P, S> onInitialState(
    props: P,
    snapshot: Snapshot?,
    workflowScope: CoroutineScope,
    proceed: (P, Snapshot?, CoroutineScope) -> S,
    session: WorkflowSession
  ): S = proceed(props, snapshot, workflowScope)

  /**
   * Intercepts calls to [StatefulWorkflow.onPropsChanged].
   */
  public fun <P, S> onPropsChanged(
    old: P,
    new: P,
    state: S,
    proceed: (P, P, S) -> S,
    session: WorkflowSession
  ): S = proceed(old, new, state)

  /**
   * Intercept a full rendering pass which involves rendering then snapshotting the workflow tree.
   * This is useful for tracing purposes.
   */
  public fun <P, R> onRenderAndSnapshot(
    renderProps: P,
    proceed: (P) -> RenderingAndSnapshot<R>,
    session: WorkflowSession
  ): RenderingAndSnapshot<R> =
    proceed(renderProps)

  /**
   * Intercepts calls to [StatefulWorkflow.render].
   */
  public fun <P, S, O, R> onRender(
    renderProps: P,
    renderState: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
    session: WorkflowSession
  ): R = proceed(renderProps, renderState, null)

  /**
   * Intercept calls to [StatefulWorkflow.snapshotState] including the children calls.
   * This is useful to intercept a rendering + snapshot traversal for tracing purposes.
   */
  public fun onSnapshotStateWithChildren(
    proceed: () -> TreeSnapshot,
    session: WorkflowSession
  ): TreeSnapshot = proceed()

  /**
   * Intercepts calls to [StatefulWorkflow.snapshotState].
   */
  public fun <S> onSnapshotState(
    state: S,
    proceed: (S) -> Snapshot?,
    session: WorkflowSession
  ): Snapshot? = proceed(state)

  /**
   * Information about the session of a workflow in the runtime that a [WorkflowInterceptor] method
   * is intercepting.
   */
  public interface WorkflowSession {
    /** The [WorkflowIdentifier] that represents the type of this workflow. */
    public val identifier: WorkflowIdentifier

    /**
     * The string key argument that was passed to [BaseRenderContext.renderChild] to render this
     * workflow.
     */
    public val renderKey: String

    /**
     * A unique value that identifies the currently-running session of this workflow in the
     * runtime. See the documentation on [WorkflowInterceptor] for more information about what this
     * value represents.
     */
    public val sessionId: Long

    /** The parent [WorkflowSession] of this workflow, or null if this is the root workflow. */
    public val parent: WorkflowSession?

    /**
     * true if this is the root workflow, in which case [parent] is null.
     */
    public val isRootWorkflow: Boolean
      get() = parent == null

    /** The [RuntimeConfig] of the runtime this session is executing in. */
    public val runtimeConfig: RuntimeConfig

    /** The optional [WorkflowTracer] of the runtime this session is executing in. */
    public val workflowTracer: WorkflowTracer?
  }

  /**
   * Provides hooks for intercepting calls to a [BaseRenderContext], to be used from [onRender].
   *
   * For use by [onRender] methods that want to hook into action and
   * side effect events. See documentation on methods for more information about the individual
   * hooks:
   *  - [RenderContextInterceptor.onActionSent]
   *  - [RenderContextInterceptor.onRunningSideEffect]
   *
   * E.g.:
   * ```
   * override fun <P, S, O, R> onRender(
   *   renderProps: P,
   *   renderState: S,
   *   proceed: (P, S, RenderContextInterceptor<P, S, O>) -> R,
   *   session: WorkflowSession
   * ): R = proceed(renderProps, renderState, object : RenderContextInterceptor<P, S, O> {
   *   override fun onActionSent(
   *     action: WorkflowAction<P, S, O>,
   *     proceed: (WorkflowAction<P, S, O>) -> Unit
   *   ) {
   *     log("Action sent: $action")
   *     proceed(action)
   *   }
   *
   *   override fun onRunningSideEffect(
   *     key: String,
   *     sideEffect: suspend () -> Unit,
   *     proceed: (key: String, sideEffect: suspend () -> Unit) -> Unit
   *   ) {
   *     proceed(key) {
   *       log("Side effect started: $key")
   *       sideEffect()
   *       log("Side effect ended: $key")
   *     }
   *   }
   * })
   * ```
   */
  public interface RenderContextInterceptor<P, S, O> {

    /**
     * Intercepts calls to [send][Sink.send] on the [BaseRenderContext.actionSink].
     *
     * This method will be called from inside the actual [Sink.send] stack frame, so any stack
     * traces captured from it will include the code that is actually making the send call.
     */
    public fun onActionSent(
      action: WorkflowAction<P, S, O>,
      proceed: (WorkflowAction<P, S, O>) -> Unit
    ) {
      proceed(action)
    }

    /**
     * Intercepts calls to [BaseRenderContext.runningSideEffect], allowing the
     * interceptor to wrap or replace the [sideEffect] and its [key]. This could
     * be used to prevent a side effect from running, or to augment it with
     * further effects.
     *
     * The [sideEffect] function will perform the actual suspending side effect, and only
     * return when the side effect is complete – this may be far in the future. This means
     * the interceptor can be notified when the side effect _ends_ by simply running code
     * after [sideEffect] returns or throws.
     *
     * The interceptor may run [sideEffect] in a different [CoroutineContext], e.g to change
     * its dispatcher or name, but should take care to use the original [Job], or otherwise
     * ensure that the structured concurrency contract is not broken.
     */
    public fun onRunningSideEffect(
      key: String,
      sideEffect: suspend () -> Unit,
      proceed: (key: String, sideEffect: suspend () -> Unit) -> Unit
    ) {
      proceed(key, sideEffect)
    }

    /**
     * Intercepts calls to [BaseRenderContext.renderChild], allowing the
     * interceptor to wrap or replace the [child] Workflow, its [childProps],
     * [key], and the [handler] function to be applied to the child's output.
     */
    public fun <CP, CO, CR> onRenderChild(
      child: Workflow<CP, CO, CR>,
      childProps: CP,
      key: String,
      handler: (CO) -> WorkflowAction<P, S, O>,
      proceed: (
        child: Workflow<CP, CO, CR>,
        childProps: CP,
        key: String,
        handler: (CO) -> WorkflowAction<P, S, O>
      ) -> CR
    ): CR = proceed(child, childProps, key, handler)

    public fun <CR> onRenderComposable(
      key: String,
      content: @Composable () -> CR,
      proceed: (
        key: String,
        content: @Composable () -> CR
      ) -> CR
    ): CR = proceed(key, content)
  }
}

/** A [WorkflowInterceptor] that does not intercept anything. */
public object NoopWorkflowInterceptor : WorkflowInterceptor

/**
 * Returns a [StatefulWorkflow] that will intercept all calls to [workflow] via this
 * [WorkflowInterceptor].
 *
 * This is called once for each instance/session of a Workflow being intercepted. So we cache the
 * render context for re-use within that [WorkflowSession].
 */
@OptIn(WorkflowExperimentalApi::class)
internal fun <P, S, O, R> WorkflowInterceptor.intercept(
  workflow: StatefulWorkflow<P, S, O, R>,
  workflowSession: WorkflowSession
): StatefulWorkflow<P, S, O, R> = if (this === NoopWorkflowInterceptor) {
  workflow
} else {
  object : SessionWorkflow<P, S, O, R>() {

    /**
     * Render context that we are passed.
     */
    private var canonicalRenderContext: StatefulWorkflow<P, S, O, R>.RenderContext? = null

    /**
     * Render context interceptor that we are passed.
     */
    private var canonicalRenderContextInterceptor: RenderContextInterceptor<P, S, O>? = null

    /**
     * Cache of the intercepted render context.
     */
    private var cachedInterceptedRenderContext: StatefulWorkflow<P, S, O, R>.RenderContext? = null

    override fun initialState(
      props: P,
      snapshot: Snapshot?,
      workflowScope: CoroutineScope
    ): S = onInitialState(props, snapshot, workflowScope, workflow::initialState, workflowSession)

    override fun onPropsChanged(
      old: P,
      new: P,
      state: S
    ): S = onPropsChanged(old, new, state, workflow::onPropsChanged, workflowSession)

    override fun render(
      renderProps: P,
      renderState: S,
      context: RenderContext
    ): R = onRender(
      renderProps,
      renderState,
      context,
      proceed = { props, state, interceptor ->
        // The `RenderContext` used *might* change - primarily in the case of our tests. E.g., The
        // `RenderTester` uses a special NoOp context to render twice to test for idempotency.
        // In order to support a changed render context but keep caching, we check to see if the
        // instance passed in has changed.
        if (cachedInterceptedRenderContext == null || canonicalRenderContext !== context ||
          canonicalRenderContextInterceptor != interceptor
        ) {
          val interceptedRenderContext = interceptor?.let { InterceptedRenderContext(context, it) }
            ?: context
          cachedInterceptedRenderContext = RenderContext(interceptedRenderContext, this)
        }
        canonicalRenderContext = context
        canonicalRenderContextInterceptor = interceptor
        // Use the intercepted RenderContext for rendering.
        workflow.render(props, state, cachedInterceptedRenderContext!!)
      },
      session = workflowSession,
    )

    override fun snapshotState(state: S) =
      onSnapshotState(state, workflow::snapshotState, workflowSession)

    override fun toString(): String = "InterceptedWorkflow($workflow, $this@intercept)"
  }
}

private class InterceptedRenderContext<P, S, O>(
  private val baseRenderContext: BaseRenderContext<P, S, O>,
  private val interceptor: RenderContextInterceptor<P, S, O>
) : BaseRenderContext<P, S, O>, Sink<WorkflowAction<P, S, O>> {
  override val actionSink: Sink<WorkflowAction<P, S, O>> get() = this
  override val workflowTracer: WorkflowTracer? = baseRenderContext.workflowTracer

  override fun send(value: WorkflowAction<P, S, O>) {
    interceptor.onActionSent(value) { interceptedAction ->
      baseRenderContext.actionSink.send(interceptedAction)
    }
  }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<P, S, O>
  ): ChildRenderingT =
    interceptor.onRenderChild(child, props, key, handler) { iChild, iProps, iKey, iHandler ->
      baseRenderContext.renderChild(iChild, iProps, iKey, iHandler)
    }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    // We don't want to invite the interceptor to shoot itself in the foot
    // by making mistakes around the `CoroutineScope` receiver, so the sideEffect
    // method it's given has no receiver. This means it's up to us to provide one, carefully.
    val withScopeReceiver = suspend {
      CoroutineScope(activeCoroutineContext()).sideEffect()
    }

    interceptor.onRunningSideEffect(key, withScopeReceiver) { iKey, iSideEffect ->
      baseRenderContext.runningSideEffect(iKey) {
        iSideEffect()
      }
    }
  }

  @OptIn(WorkflowExperimentalApi::class)
  override fun <ChildRenderingT> renderComposable(
    key: String,
    content: @Composable () -> ChildRenderingT
  ): ChildRenderingT = interceptor.onRenderComposable(
    key = key,
    content = content,
    proceed = { iKey, iContent ->
      baseRenderContext.renderComposable(
        key = iKey,
        content = iContent
      )
    }
  )

  /**
   * In a block with a CoroutineScope receiver, calls to `coroutineContext` bind
   * to `CoroutineScope.coroutineContext` instead of `suspend val coroutineContext`.
   * Call this and always get the latter.
   */
  private suspend inline fun activeCoroutineContext(): CoroutineContext {
    return coroutineContext
  }
}
