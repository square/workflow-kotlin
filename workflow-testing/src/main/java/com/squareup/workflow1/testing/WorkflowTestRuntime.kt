@file:OptIn(ExperimentalCoroutinesApi::class)
@file:Suppress("ktlint:standard:indent")

package com.squareup.workflow1.testing

import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.config.JvmTestRuntimeConfigTools
import com.squareup.workflow1.internal.util.UncaughtExceptionGuard
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFresh
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromCompleteSnapshot
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromState
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromWorkflowSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Runs a [Workflow][com.squareup.workflow1.Workflow] and provides access to its
 * [renderings][awaitNextRendering], [outputs][awaitNextOutput], and [snapshots][awaitNextSnapshot].
 *
 * For each of renderings, outputs, and snapshots, this class gives you a few ways to access
 * information about them:
 *  - [awaitNextRendering], [awaitNextOutput], [awaitNextSnapshot]
 *    - Block until something becomes available, and then return it.
 *  - [hasRendering], [hasOutput], [hasSnapshot]
 *    - Return `true` if the previous methods won't block.
 *  - [sendProps]
 *    - Send a new [PropsT] to the root workflow.
 */
public class WorkflowTestRuntime<PropsT, OutputT, RenderingT> @TestOnly internal constructor(
  private val props: MutableStateFlow<PropsT>,
  private val renderingsAndSnapshotsFlow: Flow<RenderingAndSnapshot<RenderingT>>,
  private val outputs: ReceiveChannel<OutputT>
) {

  private val renderings = Channel<RenderingT>(capacity = UNLIMITED)
  private val snapshots = Channel<TreeSnapshot>(capacity = UNLIMITED)

  internal fun collectFromWorkflowIn(scope: CoroutineScope) {
    // Handle cancellation before subscribing to flow in case the scope is cancelled already.
    scope.coroutineContext[Job]!!.invokeOnCompletion { e ->
      renderings.close(e)
      snapshots.close(e)
    }

    // We use NonCancellable so that if context is already cancelled, the operator chains below
    // are still allowed to handle the exceptions from WorkflowHost streams explicitly, since they
    // need to close the test channels.
    val realScope = scope + NonCancellable
    renderingsAndSnapshotsFlow
      .onEach { (rendering, snapshot) ->
        renderings.send(rendering)
        snapshots.send(snapshot)
      }
      .launchIn(realScope)
  }

  /**
   * True if the workflow has emitted a new rendering that is ready to be consumed.
   */
  public val hasRendering: Boolean get() = !renderings.isEmptyOrClosed

  /**
   * True if the workflow has emitted a new snapshot that is ready to be consumed.
   */
  public val hasSnapshot: Boolean get() = !snapshots.isEmptyOrClosed

  /**
   * True if the workflow has emitted a new output that is ready to be consumed.
   */
  public val hasOutput: Boolean get() = !outputs.isEmptyOrClosed

  @OptIn(DelicateCoroutinesApi::class)
  private val ReceiveChannel<*>.isEmptyOrClosed get() = isEmpty || isClosedForReceive

  /**
   * Sends [input] to the workflow.
   */
  public fun sendProps(input: PropsT) {
    props.value = input
  }

  /**
   * Blocks until the workflow emits a rendering, then returns it.
   *
   * @param timeoutMs The maximum amount of time to wait for a rendering to be emitted. If null,
   * [WorkflowTestRuntime.DEFAULT_TIMEOUT_MS] will be used instead.
   * @param skipIntermediate If true, and the workflow has emitted multiple renderings, all but the
   * most recent one will be dropped.
   */
  public fun awaitNextRendering(
    timeoutMs: Long? = null,
    skipIntermediate: Boolean = true
  ): RenderingT = renderings.receiveBlocking(timeoutMs, skipIntermediate)

  /**
   * Blocks until the workflow emits a snapshot, then returns it.
   *
   * The returned snapshot will be the snapshot only of the root workflow. It will be null if
   * `snapshotState` returned an empty [Snapshot].
   *
   * @param timeoutMs The maximum amount of time to wait for a snapshot to be emitted. If null,
   * [DEFAULT_TIMEOUT_MS] will be used instead.
   * @param skipIntermediate If true, and the workflow has emitted multiple snapshots, all but the
   * most recent one will be dropped.
   */
  public fun awaitNextSnapshot(
    timeoutMs: Long? = null,
    skipIntermediate: Boolean = true
  ): TreeSnapshot = snapshots.receiveBlocking(timeoutMs, skipIntermediate)

  /**
   * Blocks until the workflow emits an output, then returns it.
   *
   * @param timeoutMs The maximum amount of time to wait for an output to be emitted. If null,
   * [DEFAULT_TIMEOUT_MS] will be used instead.
   */
  public fun awaitNextOutput(timeoutMs: Long? = null): OutputT =
    outputs.receiveBlocking(timeoutMs, drain = false)

  /**
   * @param drain If true, this function will consume all the values currently in the channel, and
   * return the last one.
   */
  private fun <T> ReceiveChannel<T>.receiveBlocking(
    timeoutMs: Long?,
    drain: Boolean
  ): T = runBlocking {
    withTimeout(timeoutMs ?: DEFAULT_TIMEOUT_MS) {
      var item = receive()
      if (drain) {
        while (!isEmpty) {
          item = receive()
        }
      }
      return@withTimeout item
    }
  }

  public companion object {
    public const val DEFAULT_TIMEOUT_MS: Long = 500
  }
}

/**
 * Creates a [WorkflowTestRuntime] to run this workflow for unit testing.
 *
 * All workflow-related coroutines are cancelled when the block exits.
 */
@TestOnly
public fun <T, PropsT, OutputT, RenderingT>
  Workflow<PropsT, OutputT, RenderingT>.launchForTestingFromStartWith(
  props: PropsT,
  testParams: WorkflowTestParams<Nothing> = WorkflowTestParams(),
  context: CoroutineContext = EmptyCoroutineContext,
  block: WorkflowTestRuntime<PropsT, OutputT, RenderingT>.() -> T
): T = asStatefulWorkflow().launchForTestingWith(props, testParams, context, block)

/**
 * Creates a [WorkflowTestRuntime] to run this workflow for unit testing.
 *
 * All workflow-related coroutines are cancelled when the block exits.
 */
@TestOnly
public fun <T, OutputT, RenderingT>
  Workflow<Unit, OutputT, RenderingT>.launchForTestingFromStartWith(
  testParams: WorkflowTestParams<Nothing> = WorkflowTestParams(),
  context: CoroutineContext = EmptyCoroutineContext,
  block: WorkflowTestRuntime<Unit, OutputT, RenderingT>.() -> T
): T = launchForTestingFromStartWith(Unit, testParams, context, block)

/**
 * Creates a [WorkflowTestRuntime] to run this workflow for unit testing.
 * If the workflow is [stateful][StatefulWorkflow], [initialState][StatefulWorkflow.initialState]
 * is not called. Instead, the workflow is started from the given [initialState].
 *
 * All workflow-related coroutines are cancelled when the block exits.
 */
@TestOnly
public fun <T, PropsT, StateT, OutputT, RenderingT>
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.launchForTestingFromStateWith(
  props: PropsT,
  initialState: StateT,
  context: CoroutineContext = EmptyCoroutineContext,
  block: WorkflowTestRuntime<PropsT, OutputT, RenderingT>.() -> T
): T = launchForTestingWith(
  props,
  WorkflowTestParams(StartFromState(initialState)),
  context,
  block
)

/**
 * Creates a [WorkflowTestRuntime] to run this workflow for unit testing.
 * If the workflow is [stateful][StatefulWorkflow], [initialState][StatefulWorkflow.initialState]
 * is not called. Instead, the workflow is started from the given [initialState].
 *
 * All workflow-related coroutines are cancelled when the block exits.
 */
@TestOnly
public fun <StateT, OutputT, RenderingT>
  StatefulWorkflow<Unit, StateT, OutputT, RenderingT>.launchForTestingFromStateWith(
  initialState: StateT,
  context: CoroutineContext = EmptyCoroutineContext,
  block: WorkflowTestRuntime<Unit, OutputT, RenderingT>.() -> Unit
): Unit = launchForTestingFromStateWith(Unit, initialState, context, block)

/**
 * Creates a [WorkflowTestRuntime] to run this workflow for unit testing.
 *
 * All workflow-related coroutines are cancelled when the block exits.
 */
@TestOnly
public fun <T, PropsT, StateT, OutputT, RenderingT>
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.launchForTestingWith(
  props: PropsT,
  testParams: WorkflowTestParams<StateT> = WorkflowTestParams(),
  context: CoroutineContext = EmptyCoroutineContext,
  block: WorkflowTestRuntime<PropsT, OutputT, RenderingT>.() -> T
): T {
  val propsFlow = MutableStateFlow(props)

  // Any exceptions that are thrown from a launch will be reported to the coroutine's uncaught
  // exception handler, which will, by default, report them to the thread. We don't want to do that,
  // we want to throw them from the test directly so they'll fail the test and/or let the test code
  // assert on them.
  val exceptionGuard = UncaughtExceptionGuard()
  val uncaughtExceptionHandler = CoroutineExceptionHandler { _, throwable ->
    exceptionGuard.reportUncaught(throwable)
  }

  val snapshot = when (val startFrom = testParams.startFrom) {
    StartFresh -> null
    is StartFromWorkflowSnapshot -> TreeSnapshot.forRootOnly(startFrom.snapshot)
    is StartFromCompleteSnapshot -> startFrom.snapshot
    is StartFromState -> null
  }

  val workflowScope = CoroutineScope(Unconfined + context + uncaughtExceptionHandler)
  val outputs = Channel<OutputT>(capacity = UNLIMITED)
  workflowScope.coroutineContext[Job]!!.invokeOnCompletion {
    outputs.close(it)
  }
  val interceptors = testParams.createInterceptors()
  val renderingsAndSnapshots = renderWorkflowIn(
    workflow = this@launchForTestingWith,
    scope = workflowScope,
    props = propsFlow,
    initialSnapshot = snapshot,
    interceptors = interceptors,
    runtimeConfig = testParams.runtimeConfig ?: JvmTestRuntimeConfigTools.getTestRuntimeConfig()
  ) { output -> outputs.send(output) }
  val tester = WorkflowTestRuntime(propsFlow, renderingsAndSnapshots, outputs)
  tester.collectFromWorkflowIn(workflowScope)

  return exceptionGuard.runRethrowingUncaught {
    unwrapCancellationCause {
      try {
        // Since this is not a suspend function, we need to check if the scope was cancelled
        // ourselves.
        workflowScope.ensureActive()
        block(tester).also {
          workflowScope.ensureActive()
        }
      } finally {
        workflowScope.cancel(CancellationException("Test finished"))
      }
    }
  }
}

private fun WorkflowTestParams<*>.createInterceptors(): List<WorkflowInterceptor> {
  val interceptors = mutableListOf<WorkflowInterceptor>()

  if (checkRenderIdempotence) {
    interceptors += RenderIdempotencyChecker
  }

  (startFrom as? StartFromState)?.let { startFrom ->
    interceptors += object : WorkflowInterceptor {
      @Suppress("UNCHECKED_CAST")
      override fun <P, S> onInitialState(
        props: P,
        snapshot: Snapshot?,
        workflowScope: CoroutineScope,
        proceed: (P, Snapshot?, CoroutineScope) -> S,
        session: WorkflowSession
      ): S {
        return if (session.parent == null) {
          startFrom.state as S
        } else {
          proceed(props, snapshot, workflowScope)
        }
      }
    }
  }

  return interceptors
}

private fun <T> unwrapCancellationCause(block: () -> T): T {
  try {
    return block()
  } catch (e: CancellationException) {
    throw generateSequence(e as Throwable) { e.cause }
      // Stop the sequence if an exception's cause is itself.
      .runningReduce { error, cause ->
        if (cause !is CancellationException || cause === error) throw cause
        return@runningReduce cause
      }
      .firstOrNull { it !is CancellationException }
      ?: e
  }
}
