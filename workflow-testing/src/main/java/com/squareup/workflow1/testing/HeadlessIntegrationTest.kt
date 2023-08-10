package com.squareup.workflow1.testing

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.testing.WorkflowTurbine.Companion.WORKFLOW_TEST_DEFAULT_TIMEOUT_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * This is a test harness to run integration tests for a Workflow tree. The parameters passed here are
 * the same as those to start a Workflow runtime with [renderWorkflowIn] except for ignoring
 * state persistence as that is not needed for this style of test.
 *
 * The [coroutineContext] rather than a [CoroutineScope] is passed so that this harness handles the
 * scope for the Workflow runtime for you but you can still specify context for it. It defaults to
 * [Dispatchers.Main.immediate]. If you wish to use a dispatcher that skips delays, use a
 * [StandardTestDispatcher][kotlinx.coroutines.test.StandardTestDispatcher], so that the dispatcher
 * will still guarantee ordering.
 *
 * A [testTimeout] may be specified to override the default [WORKFLOW_TEST_DEFAULT_TIMEOUT_MS] for
 * any particular test. This is the max amount of time the test could spend waiting on a rendering.
 *
 * This will start the Workflow runtime (with params as passed) rooted at whatever Workflow
 * it is called on and then create a [WorkflowTurbine] for its renderings and run [testCase] on that.
 * [testCase] can thus drive the test scenario and assert against renderings.
 */
public fun <PropsT, OutputT, RenderingT> Workflow<PropsT, OutputT, RenderingT>.headlessIntegrationTest(
  props: StateFlow<PropsT>,
  coroutineContext: CoroutineContext = Dispatchers.Main.immediate,
  interceptors: List<WorkflowInterceptor> = emptyList(),
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  onOutput: suspend (OutputT) -> Unit = {},
  testTimeout: Long = WORKFLOW_TEST_DEFAULT_TIMEOUT_MS,
  testCase: suspend WorkflowTurbine<RenderingT>.() -> Unit
) {
  val workflow = this

  runTest(
    context = coroutineContext,
    timeout = testTimeout.milliseconds
  ) {
    // We use a sub-scope so that we can cancel the Workflow runtime when we are done with it so that
    // tests don't all have to do that themselves.
    val workflowRuntimeScope = CoroutineScope(coroutineContext)
    val renderings = renderWorkflowIn(
      workflow = workflow,
      props = props,
      scope = workflowRuntimeScope,
      interceptors = interceptors,
      runtimeConfig = runtimeConfig,
      onOutput = onOutput
    )

    val firstRendering = renderings.value.rendering

    // Drop one as its provided separately via `firstRendering`.
    renderings.drop(1).map {
      it.rendering
    }.test {
      val workflowTurbine = WorkflowTurbine(
        firstRendering,
        this
      )
      workflowTurbine.testCase()
      cancelAndIgnoreRemainingEvents()
    }
    workflowRuntimeScope.cancel()
  }
}

/**
 * Version of [headlessIntegrationTest] that does not require props. For Workflows that have [Unit]
 * props type.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public fun <OutputT, RenderingT> Workflow<Unit, OutputT, RenderingT>.headlessIntegrationTest(
  coroutineContext: CoroutineContext = UnconfinedTestDispatcher(),
  interceptors: List<WorkflowInterceptor> = emptyList(),
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  onOutput: suspend (OutputT) -> Unit = {},
  testTimeout: Long = WORKFLOW_TEST_DEFAULT_TIMEOUT_MS,
  testCase: suspend WorkflowTurbine<RenderingT>.() -> Unit
): Unit = headlessIntegrationTest(
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
 */
public class WorkflowTurbine<RenderingT>(
  public val firstRendering: RenderingT,
  private val receiveTurbine: ReceiveTurbine<RenderingT>
) {
  private var usedFirst = false

  /**
   * Suspend waiting for the next rendering to be produced by the Workflow runtime. Note this includes
   * the first (synchronously made) rendering.
   *
   * @return the rendering.
   */
  public suspend fun awaitNextRendering(): RenderingT {
    if (!usedFirst) {
      usedFirst = true
      return firstRendering
    }
    return receiveTurbine.awaitItem()
  }

  public suspend fun skipRenderings(count: Int) {
    val skippedCount = if (!usedFirst) {
      usedFirst = true
      count - 1
    } else {
      count
    }

    if (skippedCount > 0) {
      receiveTurbine.skipItems(skippedCount)
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
