package com.squareup.workflow1.testing

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.config.JvmTestRuntimeConfigTools
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.testing.WorkflowTurbine.Companion.WORKFLOW_TEST_DEFAULT_TIMEOUT_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * This is a test harness to run turbine like tests for a Workflow tree. The parameters passed here
 * are the same as those to start a Workflow runtime with [renderWorkflowIn] except for ignoring
 * state persistence as that is not needed for this style of test.
 *
 * The [coroutineContext] rather than a [CoroutineScope] is passed so that this harness handles the
 * scope for the Workflow runtime for you but you can still specify context for it.
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
public fun <PropsT, OutputT, RenderingT> Workflow<PropsT, OutputT, RenderingT>.renderForTest(
  props: StateFlow<PropsT>,
  coroutineContext: CoroutineContext = UnconfinedTestDispatcher(),
  interceptors: List<WorkflowInterceptor> = emptyList(),
  runtimeConfig: RuntimeConfig = JvmTestRuntimeConfigTools.getTestRuntimeConfig(),
  onOutput: suspend (OutputT) -> Unit = {},
  testTimeout: Long = WORKFLOW_TEST_DEFAULT_TIMEOUT_MS,
  testCase: suspend WorkflowTurbine<RenderingT, OutputT>.() -> Unit
) {
  val workflow = this

  runTest(
    context = coroutineContext,
    timeout = testTimeout.milliseconds
  ) {
    // We use a sub-scope so that we can cancel the Workflow runtime when we are done with it so that
    // tests don't all have to do that themselves.
    val workflowRuntimeScope = CoroutineScope(coroutineContext)

    // Capture outputs in a channel
    val outputsChannel = Channel<OutputT>(Channel.UNLIMITED)

    val renderings = renderWorkflowIn(
      workflow = workflow,
      props = props,
      scope = workflowRuntimeScope,
      interceptors = interceptors,
      runtimeConfig = runtimeConfig,
      onOutput = { output ->
        outputsChannel.send(output)
        onOutput(output)
      }
    )

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
        outputTurbine = outputTurbine
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
 * Version of [renderForTest] that does not require props. For Workflows that have [Unit]
 * props type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <OutputT, RenderingT> Workflow<Unit, OutputT, RenderingT>.renderForTest(
  coroutineContext: CoroutineContext = UnconfinedTestDispatcher(),
  interceptors: List<WorkflowInterceptor> = emptyList(),
  runtimeConfig: RuntimeConfig = JvmTestRuntimeConfigTools.getTestRuntimeConfig(),
  onOutput: suspend (OutputT) -> Unit = {},
  testTimeout: Long = WORKFLOW_TEST_DEFAULT_TIMEOUT_MS,
  testCase: suspend WorkflowTurbine<RenderingT, OutputT>.() -> Unit
): Unit = renderForTest(
  props = MutableStateFlow(Unit).asStateFlow(),
  coroutineContext = coroutineContext,
  interceptors = interceptors,
  runtimeConfig = runtimeConfig,
  onOutput = onOutput,
  testTimeout = testTimeout,
  testCase = testCase
)

/**
 * Simple wrapper around a [ReceiveTurbine] of [RenderingT] to provide convenience helper methods specific
 * to Workflow renderings.
 *
 * @property firstRendering The first rendering of the Workflow runtime is made synchronously. This is
 *   provided separately if any assertions or operations are needed from it.
 * @property firstSnapshot The first snapshot of the Workflow runtime is made synchronously. This is
 *   provided separately if any assertions or operations are needed from it.
 */
public class WorkflowTurbine<RenderingT, OutputT>(
  public val firstRendering: RenderingT,
  public val firstSnapshot: TreeSnapshot,
  private val renderingTurbine: ReceiveTurbine<RenderingT>,
  private val snapshotTurbine: ReceiveTurbine<TreeSnapshot>,
  private val outputTurbine: ReceiveTurbine<OutputT>,
) {
  private var usedFirstRendering = false
  private var usedFirstSnapshot = false

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
    return renderingTurbine.awaitItem()
  }

  /**
   * Suspend waiting for the next output to be produced by the Workflow runtime.
   *
   * @return the output.
   */
  public suspend fun awaitNextOutput(): OutputT = outputTurbine.awaitItem()

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
