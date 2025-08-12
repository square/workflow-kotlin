package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.DRAIN_EXCLUSIVE_ACTIONS
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
import com.squareup.workflow1.WorkflowInterceptor.RenderPassSkipped
import com.squareup.workflow1.WorkflowInterceptor.RenderingConflated
import com.squareup.workflow1.WorkflowInterceptor.RenderingProduced
import com.squareup.workflow1.WorkflowInterceptor.RuntimeSettled
import com.squareup.workflow1.internal.WorkStealingDispatcher
import com.squareup.workflow1.internal.WorkflowRunner
import com.squareup.workflow1.internal.chained
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Launches the [workflow] in a new coroutine in [scope] and returns a [StateFlow] of its
 * [renderings][RenderingT] and [snapshots][Snapshot]. The workflow tree is seeded with
 * [initialSnapshot] and the current value value of [props]. Subsequent values emitted from [props]
 * will be used to re-render the workflow.
 *
 * This is the primary low-level entry point into the workflow runtime. If you are writing an app,
 * you should probably be using a higher-level entry point that will also let you define UI bindings
 * for your renderings.
 *
 * ## Initialization
 *
 * When this function is called, the workflow runtime is started immediately, before the function
 * even returns. The current value of the [props] [StateFlow] is used to perform the initial render
 * pass. The result of this render pass is used to initialize the [StateFlow] of renderings and
 * snapshots that is returned.
 *
 * Once the initial render pass is complete, the workflow runtime will continue executing in a new
 * coroutine launched in [scope].
 *
 * ## Runtime Loop
 *
 * After the first render pass, the runtime loop executes as follows:
 *
 * 1. `suspend` awaiting the application of an action.
 * 1. If [DRAIN_EXCLUSIVE_ACTIONS] is enabled, process any other exclusive actions synchronously.
 * 1. Render Pass: call recurse the tree to call `render()`. (If [PARTIAL_TREE_RENDERING] is enabled
 * then only call this for dirty subtrees.)
 * 1. If [CONFLATE_STALE_RENDERINGS] is enabled, then continue to *synchronously* process any
 * available actions and do another render pass.
 * 1. Pass the updated rendering into the [StateFlow] returned from this method.
 *
 * When there is UI involved, we recommend using a `CoroutineDispatcher` in [scope]'s
 * `CoroutineContext` that runs the above runtime loop until it is stable before the next 'frame',
 * whatever frame means on your platform. Specifically, ensure that all dispatched coroutines are
 * run before the next 'frame', so that the Workflow runtime has done all the work it can.
 *
 * One way to achieve that guarantee is with an "immediate" dispatcher on the main thread - like,
 * `Dispatchers.Main.immediate` - since it will continue to run until the runtime is stable before
 * it lets any frame get updated by the main thread.
 * However, if an "immediate" dispatcher is used, then only 1 action will ever be available
 * since as soon as it is available it will resume (step #1 above) and start processing the rest of
 * the loop immediately.
 * This means that [DRAIN_EXCLUSIVE_ACTIONS] and [CONFLATE_STALE_RENDERINGS] will have no effect.
 *
 * A preferred way to achieve that is to have your dispatcher drain coroutines for each frame
 * explicitly. On Android, for example, that can be done with Compose UI's
 * `AndroidUiDispatcher.Main`.
 *
 * ## Scoping
 *
 * The workflow runtime makes use of
 * [structured concurrency](https://medium.com/@elizarov/structured-concurrency-722d765aa952).
 *
 * The runtime is started in [scope], which defines the context for the entire workflow tree – most
 * importantly, the [Job] that governs the runtime's lifetime and exception
 * reporting path, and the [CoroutineDispatcher][kotlinx.coroutines.CoroutineDispatcher] that
 * decides on what thread(s) to run workflow code. Note that if the scope's dispatcher executes
 * on threads different than the caller, then the initial render pass will occur on the current
 * thread but all subsequent render passes, and actions, will be executed on that dispatcher. This
 * shouldn't affect well-written workflows, since the render method should not perform side effects
 * anyway.
 *
 * All workers that are run by this runtime will be collected in coroutines that are children of
 * [scope]. When the root workflow emits an output, [onOutput] will be invoked in a child of
 * [scope].
 *
 * To stop the workflow runtime, simply cancel [scope]. Any running workers will be cancelled, and
 * if [onOutput] is currently running it will be cancelled as well.
 *
 * ## Error handling
 *
 * If the initial render pass throws an exception, that exception will be thrown from this function.
 * Any exceptions thrown from the runtime (and any workflows or workers) after that will bubble up
 * and be handled by [scope] (usually by cancelling it).
 *
 * Since the [onOutput] function is executed in [scope], any exceptions it throws will also bubble
 * up to [scope]. Any exceptions thrown by subscribers of the returned [StateFlow] will _not_ cancel
 * [scope] or cancel the runtime, but will be handled in the [CoroutineScope] of the subscriber.
 *
 * @param workflow
 * The root workflow to render.
 *
 * @param scope
 * The [CoroutineScope] in which to launch the workflow runtime. Any exceptions thrown
 * in any workflows, after the initial render pass, will be handled by this scope, and cancelling
 * this scope will cancel the workflow runtime and any running workers. Note that any dispatcher
 * in this scope will _not_ be used to execute the very first render pass.
 *
 * @param props
 * Specifies the initial [PropsT] to use to render the root workflow, and will cause a re-render
 * when new props are emitted. If this flow completes _after_ emitting at least one value, the
 * runtime will _not_ fail or stop, it will continue running with the last-emitted input.
 * To only pass a single props value, simply create a [MutableStateFlow] with the value.
 *
 * @param initialSnapshot
 * If not null or empty, used to restore the workflow. Should be obtained from a previous runtime's
 * [RenderingAndSnapshot].
 *
 * @param interceptors
 * An optional list of [WorkflowInterceptor]s that will wrap every workflow rendered by the runtime.
 * Interceptors will be invoked in 0-to-`length` order: the interceptor at index 0 will process the
 * workflow first, then the interceptor at index 1, etc.
 *
 * @param onOutput
 * A function that will be called whenever the root workflow emits an [OutputT]. This is a suspend
 * function, and is invoked synchronously within the runtime: if it suspends, the workflow runtime
 * will effectively be paused until it returns. This means that it will propagate backpressure if
 * used to forward outputs to a [Flow] or [Channel][kotlinx.coroutines.channels.Channel], for
 * example.
 *
 * @param runtimeConfig
 * Configuration parameters for the Workflow Runtime.
 *
 * @return
 * A [StateFlow] of [RenderingAndSnapshot]s that will emit any time the root workflow creates a new
 * rendering.
 */
@OptIn(WorkflowExperimentalRuntime::class)
public fun <PropsT, OutputT, RenderingT> renderWorkflowIn(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  scope: CoroutineScope,
  props: StateFlow<PropsT>,
  initialSnapshot: TreeSnapshot? = null,
  interceptors: List<WorkflowInterceptor> = emptyList(),
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  workflowTracer: WorkflowTracer? = null,
  onOutput: suspend (OutputT) -> Unit
): StateFlow<RenderingAndSnapshot<RenderingT>> {
  val chainedInterceptor = interceptors.chained()

  val dispatcher = if (RuntimeConfigOptions.WORK_STEALING_DISPATCHER in runtimeConfig) {
    WorkStealingDispatcher.wrapDispatcherFrom(scope.coroutineContext)
  } else {
    null
  }

  @Suppress("NAME_SHADOWING")
  val scope = dispatcher?.let { scope + dispatcher } ?: scope

  val runner = WorkflowRunner(
    scope,
    workflow,
    props,
    initialSnapshot,
    chainedInterceptor,
    runtimeConfig,
    workflowTracer
  )

  // Rendering is synchronous, so we can run the first render pass before launching the runtime
  // coroutine to calculate the initial rendering.
  val renderingsAndSnapshots = MutableStateFlow(
    try {
      runner.nextRendering().also {
        chainedInterceptor.onRuntimeUpdate(RenderingProduced)
        chainedInterceptor.onRuntimeUpdate(RuntimeSettled)
      }
    } catch (e: Throwable) {
      // If any part of the workflow runtime fails, the scope should be cancelled. We're not in a
      // coroutine yet however, so if the first render pass fails it won't cancel the runtime,
      // but this is an implementation detail so we must cancel the scope manually to keep the
      // contract.
      val cancellation =
        (e as? CancellationException) ?: CancellationException("Workflow runtime failed", e)
      runner.cancelRuntime(cancellation)
      throw e
    }
  )

  suspend fun <OutputT> sendOutput(
    actionResult: ActionProcessingResult,
    onOutput: suspend (OutputT) -> Unit
  ) {
    when (actionResult) {
      is ActionApplied<*> -> {
        @Suppress("UNCHECKED_CAST")
        (actionResult as? ActionApplied<OutputT>)?.let {
          it.output?.let { actualOutput ->
            onOutput(actualOutput.value)
          }
        }
      }

      else -> {} // no -op
    }
  }

  /**
   * If [runtimeConfig] contains [RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES] and
   * we have not changed state, then return true to short circuit the render loop.
   */
  fun shouldShortCircuitForUnchangedState(
    actionResult: ActionProcessingResult,
  ): Boolean {
    return runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES) &&
      actionResult is ActionApplied<*> &&
      !actionResult.stateChanged
  }

  /**
   * We advance the dispatcher first to allow any coroutines that were launched by the last
   * render pass to start up and potentially enqueue actions.
   */
  fun WorkStealingDispatcher.drainTasksIfPossible() {
    workflowTracer.trace("AdvancingWorkflowDispatcher") {
      advanceUntilIdle()
    }
  }

  scope.launch {
    outer@ while (isActive) {
      // It might look weird to start by waiting for an action before getting the rendering below,
      // but remember the first render pass already occurred above, before this coroutine was even
      // launched.
      var actionResult: ActionProcessingResult = runner.awaitAndApplyAction()

      if (shouldShortCircuitForUnchangedState(actionResult)) {
        chainedInterceptor.onRuntimeUpdate(RenderPassSkipped)
        chainedInterceptor.onRuntimeUpdate(RuntimeSettled)
        sendOutput(actionResult, onOutput)
        continue@outer
      }

      // After resuming from runner.awaitAndApplyAction() our coroutine could now be cancelled,
      // check so we don't surprise anyone with an unexpected rendering pass. Show's over, go home.
      if (!isActive) return@launch

      var drainingActionResult = actionResult
      var actionDrainingHasChangedState = false
      if (runtimeConfig.contains(DRAIN_EXCLUSIVE_ACTIONS)) {
        drain@ while (isActive && drainingActionResult is ActionApplied<*> &&
          drainingActionResult.output == null
        ) {
          actionDrainingHasChangedState =
            actionDrainingHasChangedState || drainingActionResult.stateChanged

          dispatcher?.drainTasksIfPossible()
          drainingActionResult = runner.applyNextAvailableTreeAction(skipDirtyNodes = true)

          // If no actions processed, then we can't apply any more actions.
          if (drainingActionResult == ActionsExhausted) break@drain

          // Update actionResult to continue on below.
          actionResult = drainingActionResult
          chainedInterceptor.onRuntimeUpdate(RenderPassSkipped)
        }
      }

      // Next Render Pass.
      var nextRenderAndSnapshot: RenderingAndSnapshot<RenderingT> = runner.nextRendering()

      if (runtimeConfig.contains(CONFLATE_STALE_RENDERINGS)) {
        conflate@ while (isActive && actionResult is ActionApplied<*> &&
          actionResult.output == null
        ) {
          actionDrainingHasChangedState =
            actionDrainingHasChangedState || actionResult.stateChanged
          // We may have more actions we can process, this rendering could be stale.
          // This will check for any actions that are immediately available and apply them.

          dispatcher?.drainTasksIfPossible()
          actionResult = runner.applyNextAvailableTreeAction()

          // If no actions processed, then no new rendering needed. Pass on to UI.
          if (actionResult == ActionsExhausted) break@conflate

          // Skip rendering if we had unchanged state, keep draining actions.
          if (shouldShortCircuitForUnchangedState(actionResult)) {
            chainedInterceptor.onRuntimeUpdate(RenderPassSkipped)
            if (actionDrainingHasChangedState) {
              // An earlier action changed state, so we need to pass the updated rendering to UI
              // in case it is the last update!
              break@conflate
            }
            chainedInterceptor.onRuntimeUpdate(RuntimeSettled)
            sendOutput(actionResult, onOutput)
            continue@outer
          }

          // Render pass for the updated state from the action applied.
          nextRenderAndSnapshot = runner.nextRendering()
          chainedInterceptor.onRuntimeUpdate(RenderingConflated)
        }
      }

      // Pass on the rendering to the UI.
      renderingsAndSnapshots.value = nextRenderAndSnapshot.also {
        chainedInterceptor.onRuntimeUpdate(RenderingProduced)
      }
      chainedInterceptor.onRuntimeUpdate(RuntimeSettled)

      // Emit the Output
      sendOutput(actionResult, onOutput)
    }
  }

  return renderingsAndSnapshots
}
