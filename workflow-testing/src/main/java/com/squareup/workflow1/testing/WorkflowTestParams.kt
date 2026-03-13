package com.squareup.workflow1.testing

import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFresh
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromCompleteSnapshot
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromState
import com.squareup.workflow1.testing.WorkflowTestParams.StartMode.StartFromWorkflowSnapshot
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly

/**
 * Controls the coroutine dispatcher used by the deprecated `launchForTesting*` helpers.
 *
 * This exists to allow a phased migration away from the deprecated APIs. The default
 * [LEGACY_UNCONFINED] preserves the pre-1.25.1 behavior where coroutines dispatched immediately,
 * so existing tests continue to pass without changes. [VIRTUAL_TIME_STANDARD] opts in to the
 * newer [kotlinx.coroutines.test.StandardTestDispatcher] behavior where coroutines must be
 * explicitly advanced.
 *
 * This enum only affects the deprecated `launchForTesting*` functions. The recommended
 * `renderForTest` API already accepts a `coroutineContext` parameter directly.
 */
public enum class DeprecatedLaunchSchedulerMode {
  /**
   * Uses [kotlinx.coroutines.test.UnconfinedTestDispatcher] — coroutines dispatch immediately.
   * This matches the behavior of the deprecated APIs before the 1.25.1 migration to
   * [kotlinx.coroutines.test.StandardTestDispatcher].
   */
  LEGACY_UNCONFINED,

  /**
   * Uses [kotlinx.coroutines.test.StandardTestDispatcher] — coroutines require explicit
   * advancement via `advanceUntilSettled()` or similar scheduler control.
   */
  VIRTUAL_TIME_STANDARD
}

/**
 * Defines configuration for workflow testing infrastructure such as `testRender`, `testFromStart`.
 * and `test`.
 *
 * @param startFrom How to start the workflow – see [StartMode].
 * @param checkRenderIdempotence If true, every render method will be called multiple times, to help
 * suss out any side effects that a render method is trying to perform. This parameter defaults to
 * `true` since the workflow contract is that `render` will be called an arbitrary number of times
 * for any given state, so performing side effects in `render` will almost always result in bugs.
 * It is recommended to leave this on, but if you need to debug a test and don't want to have to
 * deal with the extra passes, you can temporarily set it to false.
 * @param runtimeConfig Runtime configuration to apply. If `null` we use
 * [JvmTestRuntimeConfigTools.getTestRuntimeConfig][com.squareup.workflow1.config.JvmTestRuntimeConfigTools.getTestRuntimeConfig]
 * instead.
 * @param deprecatedLaunchSchedulerMode Controls which dispatcher the deprecated `launchForTesting*`
 * helpers use. Defaults to [DeprecatedLaunchSchedulerMode.LEGACY_UNCONFINED] to preserve pre-1.25.1
 * behavior. Set to [DeprecatedLaunchSchedulerMode.VIRTUAL_TIME_STANDARD] to opt in to virtual-time
 * semantics. Has no effect on the recommended `renderForTest` API. Note: if the `context` parameter
 * passed to a `launchForTesting*` function contains a dispatcher, that dispatcher takes precedence
 * over the one selected by this mode.
 * @param autoAdvanceOnStartup Controls whether `renderForTest` and the deprecated
 * `launchForTesting*` wrappers call `advanceUntilIdle()` before capturing the first rendering.
 * Defaults to `true` for compatibility with existing tests. Set to `false` for interaction-first
 * tests that need to assert against initial UI before time-based workers are advanced.
 * @param autoAdvanceBeforeAwait Controls whether `awaitNextRendering`, `awaitNextOutput`, and
 * `awaitNextSnapshot` automatically advance the test scheduler before waiting for the next item.
 * Defaults to `true` for compatibility.
 * @param autoAdvanceBeforeHasCheck Controls whether `hasRendering`, `hasOutput`, and `hasSnapshot`
 * automatically advance the test scheduler before checking channel state. Defaults to `true` for
 * compatibility.
 */
@TestOnly
public class WorkflowTestParams<out StateT>(
  public val startFrom: StartMode<StateT> = StartFresh,
  public val checkRenderIdempotence: Boolean = true,
  public val runtimeConfig: RuntimeConfig? = null,
  public val deprecatedLaunchSchedulerMode: DeprecatedLaunchSchedulerMode =
    DeprecatedLaunchSchedulerMode.LEGACY_UNCONFINED,
  public val autoAdvanceOnStartup: Boolean = true,
  public val autoAdvanceBeforeAwait: Boolean = true,
  public val autoAdvanceBeforeHasCheck: Boolean = true
) {
  public constructor(
    startFrom: StartMode<StateT>,
    checkRenderIdempotence: Boolean,
    runtimeConfig: RuntimeConfig?,
    deprecatedLaunchSchedulerMode: DeprecatedLaunchSchedulerMode,
  ) : this(
    startFrom = startFrom,
    checkRenderIdempotence = checkRenderIdempotence,
    runtimeConfig = runtimeConfig,
    deprecatedLaunchSchedulerMode = deprecatedLaunchSchedulerMode,
    autoAdvanceOnStartup = true,
    autoAdvanceBeforeAwait = true,
    autoAdvanceBeforeHasCheck = true,
  )

  public constructor(
    startFrom: StartMode<StateT>,
    checkRenderIdempotence: Boolean,
    runtimeConfig: RuntimeConfig?,
    deprecatedLaunchSchedulerMode: DeprecatedLaunchSchedulerMode,
    autoAdvanceOnStartup: Boolean,
  ) : this(
    startFrom = startFrom,
    checkRenderIdempotence = checkRenderIdempotence,
    runtimeConfig = runtimeConfig,
    deprecatedLaunchSchedulerMode = deprecatedLaunchSchedulerMode,
    autoAdvanceOnStartup = autoAdvanceOnStartup,
    autoAdvanceBeforeAwait = true,
    autoAdvanceBeforeHasCheck = true,
  )

  /**
   * Defines how to start the workflow for tests.
   *
   * See the documentation on individual cases for more information:
   *  - [StartFresh]
   *  - [StartFromWorkflowSnapshot]
   *  - [StartFromCompleteSnapshot]
   *  - [StartFromState]
   */
  public sealed class StartMode<out StateT> {
    /**
     * Starts the workflow from its initial state (as specified by
     * [initial state][com.squareup.workflow1.StatefulWorkflow.initialState]), with a null snapshot.
     */
    public object StartFresh : StartMode<Nothing>()

    /**
     * Starts the workflow from its initial state (as specified by
     * [initial state][com.squareup.workflow1.StatefulWorkflow.initialState]), with a non-null
     * snapshot.  Only applies to [StatefulWorkflow][com.squareup.workflow1.StatelessWorkflow]s.
     *
     * This differs from [StartFromCompleteSnapshot] because it represents only the snapshot for
     * the root workflow, without any of the snapshots of its children or other bookkeeping data
     * added by the workflow runtime.
     *
     * @param snapshot A [Snapshot] that can be directly parsed by a workflow's `initialState`
     * method. For workflow trees, this is only the snapshot of the _root_ workflow, as returned by
     * [snapshotState][com.squareup.workflow1.StatefulWorkflow.snapshotState]. To test with a
     * complete snapshot of the entire workflow tree, use [StartFromCompleteSnapshot].
     */
    public class StartFromWorkflowSnapshot(public val snapshot: Snapshot) : StartMode<Nothing>()

    /**
     * Starts the workflow from its initial state (as specified by
     * [initial state][com.squareup.workflow1.StatefulWorkflow.initialState]), with a non-null
     * snapshot. Only applies to [StatefulWorkflow][com.squareup.workflow1.StatelessWorkflow]s.
     *
     * This differs from [StartFromWorkflowSnapshot] because it represents a complete snapshot of
     * the entire tree, not just the individual snapshot for the root workflow.
     *
     * @param snapshot A [Snapshot] that is the entire snapshot from the workflow tree, as returned
     * by `WorkflowTester.awaitNextSnapshot`. To test with only the snapshot returned by
     * [snapshotState][com.squareup.workflow1.StatefulWorkflow.snapshotState], use
     * [StartFromWorkflowSnapshot].
     */
    public class StartFromCompleteSnapshot(public val snapshot: TreeSnapshot) : StartMode<Nothing>()

    /**
     * Starts the workflow from an exact state. Only applies to
     * [StatefulWorkflow][com.squareup.workflow1.StatelessWorkflow]s.
     */
    public class StartFromState<StateT>(public val state: StateT) : StartMode<StateT>()
  }
}

// Helper function to create interceptors from WorkflowTestParams
public fun <StateT> WorkflowTestParams<StateT>.createInterceptors(): List<WorkflowInterceptor> {
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
