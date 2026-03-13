@file:OptIn(ExperimentalCoroutinesApi::class)
@file:Suppress("ktlint:standard:indent", "DEPRECATION")

package com.squareup.workflow1.testing

import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromState
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 *    - Suspend until something becomes available, and then return it.
 *  - [hasRendering], [hasOutput], [hasSnapshot]
 *    - Return `true` if the previous methods won't suspend.
 *  - [sendProps]
 *    - Send a new [PropsT] to the root workflow.
 */
@Deprecated("Use renderForTest and WorkflowTurbine instead.")
public class WorkflowTestRuntime<PropsT, OutputT, RenderingT> @TestOnly internal constructor(
  private val props: MutableStateFlow<PropsT>,
  private val turbine: WorkflowTurbine<RenderingT, OutputT>,
  private val autoAdvanceBeforeAwait: Boolean,
  private val autoAdvanceBeforeHasCheck: Boolean,
) {

  /**
   * Advances the test scheduler so that the runtime processes all pending actions.
   *
   * This may be called automatically by [awaitNextRendering], [awaitNextOutput],
   * [awaitNextSnapshot], and the [hasRendering]/[hasOutput]/[hasSnapshot] properties depending on
   * [WorkflowTestParams.autoAdvanceBeforeAwait] and
   * [WorkflowTestParams.autoAdvanceBeforeHasCheck]. You only need to call this explicitly when you
   * want to process pending actions when you are not already awaiting a rendering or output — for
   * example, to assert on side effects triggered by an action that doesn't change state or produce
   * output.
   *
   * With a non-immediate dispatcher (like [StandardTestDispatcher]), this drains all pending
   * coroutines so the runtime processes queued actions. With an immediate dispatcher, this is
   * effectively a no-op.
   */
  public fun advanceUntilSettled() {
    turbine.advanceUntilSettled()
  }

  /**
   * True if the workflow has emitted a new rendering that is ready to be consumed.
   */
  public val hasRendering: Boolean
    @OptIn(DelicateCoroutinesApi::class)
    get() {
      if (autoAdvanceBeforeHasCheck) {
        advanceUntilSettled()
      }
      return !turbine.usedFirstRendering || !turbine.renderingChannel.isEmpty
    }

  /**
   * True if the workflow has emitted a new snapshot that is ready to be consumed.
   */
  public val hasSnapshot: Boolean
    @OptIn(DelicateCoroutinesApi::class)
    get() {
      if (autoAdvanceBeforeHasCheck) {
        advanceUntilSettled()
      }
      return !turbine.usedFirstSnapshot || !turbine.snapshotChannel.isEmpty
    }

  /**
   * True if the workflow has emitted a new output that is ready to be consumed.
   */
  public val hasOutput: Boolean
    @OptIn(DelicateCoroutinesApi::class)
    get() {
      if (autoAdvanceBeforeHasCheck) {
        advanceUntilSettled()
      }
      return !turbine.outputChannel.isEmpty
    }

  /**
   * Sends [input] to the workflow and advances the runtime so that it processes the new props.
   *
   * After calling this, the workflow will have rendered with the new props and any resulting
   * actions will have been processed. You can immediately call [awaitNextRendering] or
   * [hasRendering] without needing a separate [advanceUntilSettled] call.
   *
   * Note: if you trigger actions via callbacks on a rendering (not via [sendProps]), you still
   * need to call [advanceUntilSettled] explicitly (or await a rendering/output) before asserting.
   */
  public fun sendProps(input: PropsT) {
    props.value = input
    advanceUntilSettled()
  }

  /**
   * Suspends until the workflow emits a rendering, then returns it.
   *
   * @param timeoutMs The maximum amount of time to wait for a rendering to be emitted. If null,
   * [WorkflowTestRuntime.DEFAULT_TIMEOUT_MS] will be used instead.
   * @param skipIntermediate If true, and the workflow has emitted multiple renderings, all but the
   * most recent one will be dropped.
   * @param advanceScheduler If true, call [advanceUntilSettled] before waiting.
   */
  public suspend fun awaitNextRendering(
    timeoutMs: Long? = null,
    skipIntermediate: Boolean = true,
    advanceScheduler: Boolean = autoAdvanceBeforeAwait
  ): RenderingT {
    val block: suspend () -> RenderingT = {
      var rendering = turbine.awaitNextRendering(advanceScheduler = advanceScheduler)
      @OptIn(DelicateCoroutinesApi::class)
      if (skipIntermediate) {
        while (!turbine.renderingChannel.isEmpty) {
          rendering = turbine.awaitNextRendering(advanceScheduler = advanceScheduler)
        }
      }
      rendering
    }
    return if (timeoutMs != null) withTimeout(timeoutMs) { block() } else block()
  }

  /**
   * Suspends until the workflow emits a snapshot, then returns it.
   *
   * @param timeoutMs The maximum amount of time to wait for a snapshot to be emitted. If null,
   * [DEFAULT_TIMEOUT_MS] will be used instead.
   * @param skipIntermediate If true, and the workflow has emitted multiple snapshots, all but the
   * most recent one will be dropped.
   * @param advanceScheduler If true, call [advanceUntilSettled] before waiting.
   */
  public suspend fun awaitNextSnapshot(
    timeoutMs: Long? = null,
    skipIntermediate: Boolean = true,
    advanceScheduler: Boolean = autoAdvanceBeforeAwait
  ): TreeSnapshot {
    val block: suspend () -> TreeSnapshot = {
      var snapshot = turbine.awaitNextSnapshot(advanceScheduler = advanceScheduler)
      @OptIn(DelicateCoroutinesApi::class)
      if (skipIntermediate) {
        while (!turbine.snapshotChannel.isEmpty) {
          snapshot = turbine.awaitNextSnapshot(advanceScheduler = advanceScheduler)
        }
      }
      snapshot
    }
    return if (timeoutMs != null) withTimeout(timeoutMs) { block() } else block()
  }

  /**
   * Suspends until the workflow emits an output, then returns it.
   *
   * @param timeoutMs The maximum amount of time to wait for an output to be emitted. If null,
   * [DEFAULT_TIMEOUT_MS] will be used instead.
   * @param advanceScheduler If true, call [advanceUntilSettled] before waiting.
   */
  public suspend fun awaitNextOutput(
    timeoutMs: Long? = null,
    advanceScheduler: Boolean = autoAdvanceBeforeAwait
  ): OutputT {
    return if (timeoutMs != null) {
      withTimeout(timeoutMs) { turbine.awaitNextOutput(advanceScheduler = advanceScheduler) }
    } else {
      turbine.awaitNextOutput(advanceScheduler = advanceScheduler)
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
@Deprecated("Use renderForTest and WorkflowTurbine instead.")
@TestOnly
public fun <T, PropsT, OutputT, RenderingT>
  Workflow<PropsT, OutputT, RenderingT>.launchForTestingFromStartWith(
  props: PropsT,
  testParams: WorkflowTestParams<Nothing> = WorkflowTestParams(),
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend WorkflowTestRuntime<PropsT, OutputT, RenderingT>.() -> T
): T = asStatefulWorkflow().launchForTestingWith(props, testParams, context, block)

/**
 * Creates a [WorkflowTestRuntime] to run this workflow for unit testing.
 *
 * All workflow-related coroutines are cancelled when the block exits.
 */
@Deprecated("Use renderForTest and WorkflowTurbine instead.")
@TestOnly
public fun <T, OutputT, RenderingT>
  Workflow<Unit, OutputT, RenderingT>.launchForTestingFromStartWith(
  testParams: WorkflowTestParams<Nothing> = WorkflowTestParams(),
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend WorkflowTestRuntime<Unit, OutputT, RenderingT>.() -> T
): T = launchForTestingFromStartWith(Unit, testParams, context, block)

/**
 * Creates a [WorkflowTestRuntime] to run this workflow for unit testing.
 * If the workflow is [stateful][StatefulWorkflow], [initialState][StatefulWorkflow.initialState]
 * is not called. Instead, the workflow is started from the given [initialState].
 *
 * All workflow-related coroutines are cancelled when the block exits.
 */
@Deprecated("Use renderForTest and WorkflowTurbine instead.")
@TestOnly
public fun <T, PropsT, StateT, OutputT, RenderingT>
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.launchForTestingFromStateWith(
  props: PropsT,
  initialState: StateT,
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend WorkflowTestRuntime<PropsT, OutputT, RenderingT>.() -> T
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
@Deprecated("Use renderForTest and WorkflowTurbine instead.")
@TestOnly
public fun <StateT, OutputT, RenderingT>
  StatefulWorkflow<Unit, StateT, OutputT, RenderingT>.launchForTestingFromStateWith(
  initialState: StateT,
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend WorkflowTestRuntime<Unit, OutputT, RenderingT>.() -> Unit
): Unit = launchForTestingFromStateWith(Unit, initialState, context, block)

/**
 * Creates a [WorkflowTestRuntime] to run this workflow for unit testing.
 *
 * All workflow-related coroutines are cancelled when the block exits.
 */
@Deprecated("Use renderForTest and WorkflowTurbine instead.")
@TestOnly
public fun <T, PropsT, StateT, OutputT, RenderingT>
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.launchForTestingWith(
  props: PropsT,
  testParams: WorkflowTestParams<StateT> = WorkflowTestParams(),
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend WorkflowTestRuntime<PropsT, OutputT, RenderingT>.() -> T
): T {
  val propsFlow = MutableStateFlow(props)
  val schedulerContext = when (testParams.deprecatedLaunchSchedulerMode) {
    DeprecatedLaunchSchedulerMode.LEGACY_UNCONFINED -> UnconfinedTestDispatcher()
    DeprecatedLaunchSchedulerMode.VIRTUAL_TIME_STANDARD -> StandardTestDispatcher()
  }
  // Strip Job from the caller's context so it doesn't interfere with the runtime scope's
  // lifecycle management. Other context elements (names, coroutine names, etc.) are preserved.
  val safeContext = context.minusKey(Job)
  var result: T? = null
  renderForTest(
    props = propsFlow,
    testParams = testParams,
    coroutineContext = schedulerContext + safeContext,
  ) {
    val runtime = WorkflowTestRuntime(
      props = propsFlow,
      turbine = this,
      autoAdvanceBeforeAwait = testParams.autoAdvanceBeforeAwait,
      autoAdvanceBeforeHasCheck = testParams.autoAdvanceBeforeHasCheck,
    )
    result = runtime.block()
  }
  @Suppress("UNCHECKED_CAST")
  return result as T
}
