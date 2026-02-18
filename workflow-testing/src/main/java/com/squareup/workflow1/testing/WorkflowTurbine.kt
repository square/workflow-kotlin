package com.squareup.workflow1.testing

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.turbineScope
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.config.JvmTestRuntimeConfigTools
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFresh
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromCompleteSnapshot
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromState
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromWorkflowSnapshot
import com.squareup.workflow1.testing.WorkflowTurbine.Companion.WORKFLOW_TEST_DEFAULT_TIMEOUT_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * This is a test harness to run turbine like tests for a Workflow tree. The parameters passed here
 * are the same as those to start a Workflow runtime with [renderWorkflowIn] except for ignoring
 * state persistence as that is not needed for this style of test.
 *
 * The [coroutineContext] rather than a [CoroutineScope] is passed so that this harness handles the
 * scope for the Workflow runtime for you, but you can still specify context for it.
 *
 * A [testTimeout] may be specified to override the default [WORKFLOW_TEST_DEFAULT_TIMEOUT_MS] for
 * any particular test. This is the max amount of time the test could spend waiting on a rendering.
 *
 * This will start the Workflow runtime (with params as passed) rooted at whatever Workflow
 * it is called on and then create a [WorkflowTurbine] for its renderings and run [testCase] on that.
 * [testCase] can thus drive the test scenario and assert against renderings.
 *
 * The default [RuntimeConfig] will be the one specified via [JvmTestRuntimeConfigTools].
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <PropsT, StateT, OutputT, RenderingT>
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.renderForTest(
    props: StateFlow<PropsT>,
    testParams: WorkflowTestParams<StateT> = WorkflowTestParams(),
    coroutineContext: CoroutineContext = StandardTestDispatcher(),
    onOutput: suspend (OutputT) -> Unit = {},
    testTimeout: Long = WORKFLOW_TEST_DEFAULT_TIMEOUT_MS,
    testCase: suspend WorkflowTurbine<RenderingT, OutputT>.() -> Unit
  ) {
  val workflow: Workflow<PropsT, OutputT, RenderingT> = this

  // Determine the initial snapshot based on startFrom mode
  val initialSnapshot = when (val startFrom = testParams.startFrom) {
    StartFresh -> null
    is StartFromWorkflowSnapshot -> TreeSnapshot.forRootOnly(startFrom.snapshot)
    is StartFromCompleteSnapshot -> startFrom.snapshot
    is StartFromState -> null
  }

  val interceptors = testParams.createInterceptors()

  val runtimeConfig = testParams.runtimeConfig ?: JvmTestRuntimeConfigTools.getTestRuntimeConfig()

  runTest(
    context = coroutineContext,
    timeout = testTimeout.milliseconds
  ) {
    // We use a sub-scope so that we can cancel the Workflow runtime when we are done with it so that
    // tests don't all have to do that themselves.
    val workflowRuntimeScope = CoroutineScope(coroutineContext)

    val outputsChannel = Channel<OutputT>(Channel.UNLIMITED)

    val renderings = renderWorkflowIn(
      workflow = workflow,
      props = props,
      scope = workflowRuntimeScope,
      initialSnapshot = initialSnapshot,
      interceptors = interceptors,
      runtimeConfig = runtimeConfig,
      onOutput = { output ->
        outputsChannel.send(output)
        onOutput(output)
      }
    )

    // Advance the scheduler to start the runtime loop coroutine. With StandardTestDispatcher,
    // scope.launch {} dispatches but doesn't start the coroutine until the scheduler is advanced.
    // This ensures the runtime loop is running and waiting for actions before the test begins.
    testScheduler.advanceUntilIdle()

    val firstRendering = renderings.value.rendering
    val firstSnapshot = renderings.value.snapshot

    // Share the RenderingAndSnapshot flow so multiple subscribers can collect from it
    // Use workflowRuntimeScope so it's cancelled when the workflow is cancelled
    val sharedRenderings = renderings.drop(1)
      .shareIn(
        scope = workflowRuntimeScope,
        started = SharingStarted.Eagerly,
        replay = 0
      )

    // Use turbineScope to test multiple flows
    turbineScope {
      // Map the shared flow to extract renderings and snapshots separately
      val renderingTurbine = sharedRenderings.map { it.rendering }
        .testIn(backgroundScope, timeout = testTimeout.milliseconds, name = "renderings")
      val snapshotTurbine = sharedRenderings.map { it.snapshot }
        .testIn(backgroundScope, timeout = testTimeout.milliseconds, name = "snapshots")
      val outputTurbine = outputsChannel.receiveAsFlow()
        .testIn(backgroundScope, timeout = testTimeout.milliseconds, name = "outputs")

      val workflowTurbine = WorkflowTurbine(
        firstRendering = firstRendering,
        firstSnapshot = firstSnapshot,
        renderingTurbine = renderingTurbine,
        snapshotTurbine = snapshotTurbine,
        outputTurbine = outputTurbine,
        testScheduler = testScheduler
      )
      workflowTurbine.testCase()

      // Cancel all turbines
      renderingTurbine.cancel()
      snapshotTurbine.cancel()
      outputTurbine.cancel()
    }

    workflowRuntimeScope.cancel()
  }
}

/**
 * Version of [renderForTest] that does not require props. For [StatefulWorkflow]s that have [Unit]
 * props type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <StateT, OutputT, RenderingT>
  StatefulWorkflow<Unit, StateT, OutputT, RenderingT>.renderForTest(
    testParams: WorkflowTestParams<StateT> = WorkflowTestParams(),
    coroutineContext: CoroutineContext = StandardTestDispatcher(),
    onOutput: suspend (OutputT) -> Unit = {},
    testTimeout: Long = WORKFLOW_TEST_DEFAULT_TIMEOUT_MS,
    testCase: suspend WorkflowTurbine<RenderingT, OutputT>.() -> Unit
  ): Unit = renderForTest(
  props = MutableStateFlow(Unit).asStateFlow(),
  testParams = testParams,
  coroutineContext = coroutineContext,
  onOutput = onOutput,
  testTimeout = testTimeout,
  testCase = testCase
)

/**
 * Version of [renderForTest] for any [Workflow]
 * that accepts [WorkflowTestParams] for configuring the test,
 * including starting from a specific state or snapshot.
 *
 * @param props StateFlow of props to send to the workflow.
 * @param testParams Test configuration parameters. See [WorkflowTestParams] for details.
 * @param coroutineContext Optional [CoroutineContext] to use for the test.
 * @param onOutput Callback for workflow outputs.
 * @param testTimeout Maximum time to wait for workflow operations in milliseconds.
 * @param testCase The test code to run with access to the [WorkflowTurbine].
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <PropsT, OutputT, RenderingT> Workflow<PropsT, OutputT, RenderingT>.renderForTestForStartWith(
  props: StateFlow<PropsT>,
  testParams: WorkflowTestParams<Nothing> = WorkflowTestParams(),
  coroutineContext: CoroutineContext = StandardTestDispatcher(),
  interceptors: List<WorkflowInterceptor> = emptyList(),
  onOutput: suspend (OutputT) -> Unit = {},
  testTimeout: Long = WORKFLOW_TEST_DEFAULT_TIMEOUT_MS,
  testCase: suspend WorkflowTurbine<RenderingT, OutputT>.() -> Unit
): Unit = asStatefulWorkflow().renderForTest(
  props,
  testParams,
  coroutineContext,
  onOutput,
  testTimeout,
  testCase
)

/**
 * Version of [renderForTest] for any [Workflow]
 * that accepts [WorkflowTestParams] and doesn't require props.
 * For Workflows that have [Unit] props type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <OutputT, RenderingT> Workflow<Unit, OutputT, RenderingT>.renderForTestForStartWith(
  coroutineContext: CoroutineContext = StandardTestDispatcher(),
  testParams: WorkflowTestParams<Nothing> = WorkflowTestParams(),
  interceptors: List<WorkflowInterceptor> = emptyList(),
  onOutput: suspend (OutputT) -> Unit = {},
  testTimeout: Long = WORKFLOW_TEST_DEFAULT_TIMEOUT_MS,
  testCase: suspend WorkflowTurbine<RenderingT, OutputT>.() -> Unit
): Unit = renderForTestForStartWith(
  props = MutableStateFlow(Unit).asStateFlow(),
  testParams = testParams,
  coroutineContext = coroutineContext,
  interceptors = interceptors,
  onOutput = onOutput,
  testTimeout = testTimeout,
  testCase = testCase
)

/**
 * Convenience function to test a workflow starting from a specific state.
 *
 * This is equivalent to calling [renderForTest] with
 * `testParams = WorkflowTestParams(startFrom = StartFromState(initialState))`.
 *
 * @param props StateFlow of props to send to the workflow.
 * @param initialState The state to start the workflow from.
 * @param coroutineContext Optional [CoroutineContext] to use for the test.
 * @param onOutput Callback for workflow outputs.
 * @param testTimeout Maximum time to wait for workflow operations in milliseconds.
 * @param testCase The test code to run with access to the [WorkflowTurbine].
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <PropsT, StateT, OutputT, RenderingT>
  StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>.renderForTestFromStateWith(
    props: StateFlow<PropsT>,
    initialState: StateT,
    coroutineContext: CoroutineContext = StandardTestDispatcher(),
    onOutput: suspend (OutputT) -> Unit = {},
    testTimeout: Long = WORKFLOW_TEST_DEFAULT_TIMEOUT_MS,
    testCase: suspend WorkflowTurbine<RenderingT, OutputT>.() -> Unit
  ): Unit = renderForTest(
  props = props,
  testParams = WorkflowTestParams(startFrom = StartFromState(initialState)),
  coroutineContext = coroutineContext,
  onOutput = onOutput,
  testTimeout = testTimeout,
  testCase = testCase
)

/**
 * Convenience function to test a workflow starting from a specific state.
 * Version for workflows with [Unit] props.
 *
 * @param initialState The state to start the workflow from.
 * @param coroutineContext Optional [CoroutineContext] to use for the test.
 * @param onOutput Callback for workflow outputs.
 * @param testTimeout Maximum time to wait for workflow operations in milliseconds.
 * @param testCase The test code to run with access to the [WorkflowTurbine].
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <StateT, OutputT, RenderingT>
  StatefulWorkflow<Unit, StateT, OutputT, RenderingT>.renderForTestFromStateWith(
    initialState: StateT,
    coroutineContext: CoroutineContext = StandardTestDispatcher(),
    onOutput: suspend (OutputT) -> Unit = {},
    testTimeout: Long = WORKFLOW_TEST_DEFAULT_TIMEOUT_MS,
    testCase: suspend WorkflowTurbine<RenderingT, OutputT>.() -> Unit
  ): Unit = renderForTestFromStateWith(
  props = MutableStateFlow(Unit).asStateFlow(),
  initialState = initialState,
  coroutineContext = coroutineContext,
  onOutput = onOutput,
  testTimeout = testTimeout,
  testCase = testCase
)

/**
 * Provides independent access to three flows emitted by the workflow runtime: renderings, snapshots,
 * and outputs. Uses [shareIn] to broadcast the combined rendering/snapshot flow to multiple turbines,
 * ensuring all emissions are available to all flows without race conditions.
 *
 * @property firstRendering The first rendering, made synchronously when the workflow runtime starts.
 * @property firstSnapshot The first snapshot, made synchronously when the workflow runtime starts.
 * @property renderingTurbine Turbine for consuming subsequent renderings.
 * @property snapshotTurbine Turbine for consuming subsequent snapshots.
 * @property outputTurbine Turbine for consuming workflow outputs.
 */
public class WorkflowTurbine<RenderingT, OutputT>(
  public val firstRendering: RenderingT,
  public val firstSnapshot: TreeSnapshot,
  private val renderingTurbine: ReceiveTurbine<RenderingT>,
  private val snapshotTurbine: ReceiveTurbine<TreeSnapshot>,
  private val outputTurbine: ReceiveTurbine<OutputT>,
  private val testScheduler: TestCoroutineScheduler? = null,
) {
  internal var usedFirstRendering = false
  internal var usedFirstSnapshot = false

  @OptIn(DelicateCoroutinesApi::class)
  internal val renderingChannel get() = renderingTurbine.asChannel()

  @OptIn(DelicateCoroutinesApi::class)
  internal val snapshotChannel get() = snapshotTurbine.asChannel()

  @OptIn(DelicateCoroutinesApi::class)
  internal val outputChannel get() = outputTurbine.asChannel()

  /**
   * Advances the [testScheduler] passed in for this [WorkflowTurbine].
   *
   * This is called automatically by [awaitNextRendering], [awaitNextOutput], [awaitNextSnapshot],
   * and [skipRenderings]. You only need to call this explicitly when you want to process pending
   * actions without awaiting a rendering or output — for example, to assert on side effects
   * triggered by an action that doesn't change state or produce output.
   *
   * With a non-immediate dispatcher (like [StandardTestDispatcher]), this drains all pending
   * coroutines so the runtime processes queued actions. With an immediate dispatcher, this is
   * effectively a no-op.
   */
  public fun awaitRuntimeSettled() {
    testScheduler?.advanceUntilIdle()
  }

  /**
   * Suspend waiting for the next rendering to be produced by the Workflow runtime. Note this includes
   * the first (synchronously made) rendering.
   *
   * @return the rendering.
   */
  public suspend fun awaitNextRendering(): RenderingT {
    if (!usedFirstRendering) {
      usedFirstRendering = true
      return firstRendering
    }
    awaitRuntimeSettled()
    return renderingTurbine.awaitItem()
  }

  /**
   * Suspend waiting for the next output to be produced by the Workflow runtime.
   *
   * @return the output.
   */
  public suspend fun awaitNextOutput(): OutputT {
    awaitRuntimeSettled()
    return outputTurbine.awaitItem()
  }

  /**
   * Suspend waiting for the next snapshot to be produced by the Workflow runtime. Note this includes
   * the first (synchronously made) snapshot.
   *
   * @return the snapshot.
   */
  public suspend fun awaitNextSnapshot(): TreeSnapshot {
    if (!usedFirstSnapshot) {
      usedFirstSnapshot = true
      return firstSnapshot
    }
    awaitRuntimeSettled()
    return snapshotTurbine.awaitItem()
  }

  public suspend fun skipRenderings(count: Int) {
    val skippedCount = if (!usedFirstRendering) {
      usedFirstRendering = true
      count - 1
    } else {
      count
    }

    if (skippedCount > 0) {
      awaitRuntimeSettled()
      renderingTurbine.skipItems(skippedCount)
    }
  }

  /**
   * Suspend waiting for the next rendering to be produced by the Workflow runtime that satisfies the
   * [predicate].
   *
   * @return the rendering.
   */
  public suspend fun awaitNextRenderingSatisfying(
    predicate: (RenderingT) -> Boolean
  ): RenderingT {
    var rendering = awaitNextRendering()
    while (!predicate(rendering)) {
      rendering = awaitNextRendering()
    }
    return rendering
  }

  /**
   * Suspend waiting for the next rendering which satisfies [precondition], can successfully be mapped
   * using [map] and satisfies the [satisfying] predicate when called on the [T] rendering after it
   * has been mapped.
   *
   * @return the mapped rendering as [T]
   */
  public suspend fun <T> awaitNext(
    precondition: (RenderingT) -> Boolean = { true },
    map: (RenderingT) -> T,
    satisfying: T.() -> Boolean = { true }
  ): T =
    map(
      awaitNextRenderingSatisfying {
        precondition(it) &&
          with(map(it)) {
            this.satisfying()
          }
      }
    )

  public companion object {
    /**
     * Default timeout to use while waiting for renderings.
     */
    public const val WORKFLOW_TEST_DEFAULT_TIMEOUT_MS: Long = 60_000L
  }
}
