package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
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
import kotlinx.coroutines.yield

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
      runner.nextRendering()
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
    conflationHasChangedState: Boolean = false
  ): Boolean {
    return runtimeConfig.contains(RENDER_ONLY_WHEN_STATE_CHANGES) &&
      actionResult is ActionApplied<*> && !actionResult.stateChanged && !conflationHasChangedState
  }

  scope.launch {
    outer@ while (isActive) {
      // It might look weird to start by processing an action before getting the rendering below,
      // but remember the first render pass already occurred above, before this coroutine was even
      // launched.
      var actionResult: ActionProcessingResult = runner.processAction()

      if (shouldShortCircuitForUnchangedState(actionResult)) {
        sendOutput(actionResult, onOutput)
        continue@outer
      }

      // After resuming from runner.processAction() our coroutine could now be cancelled, check so
      // we don't surprise anyone with an unexpected rendering pass. Show's over, go home.
      if (!isActive) return@launch

      // Next Render Pass.
      var nextRenderAndSnapshot: RenderingAndSnapshot<RenderingT> = runner.nextRendering()

      if (runtimeConfig.contains(CONFLATE_STALE_RENDERINGS)) {
        var conflationHasChangedState = false
        conflate@ while (isActive && actionResult is ActionApplied<*> && actionResult.output == null) {
          conflationHasChangedState = conflationHasChangedState || actionResult.stateChanged
          // We start by yielding, because if we are on an Unconfined dispatcher, we want to give
          // other signals (like Workers listening to the same result) a chance to get dispatched
          // and queue their actions.
          yield()
          // We may have more actions we can process, this rendering could be stale.
          actionResult = runner.processAction(waitForAnAction = false)

          // If no actions processed, then no new rendering needed. Pass on to UI.
          if (actionResult == ActionsExhausted) break@conflate

          // Skip rendering if we had unchanged state, keep draining actions.
          if (shouldShortCircuitForUnchangedState(
              actionResult = actionResult,
              conflationHasChangedState = conflationHasChangedState
            )
          ) {
            sendOutput(actionResult, onOutput)
            continue@outer
          }

          // Make sure the runtime has not been cancelled from runner.processAction()
          if (!isActive) return@launch

          nextRenderAndSnapshot = runner.nextRendering()
        }
      }

      // Pass on the rendering to the UI.
      renderingsAndSnapshots.value = nextRenderAndSnapshot

      // Emit the Output
      sendOutput(actionResult, onOutput)
    }
  }

  return renderingsAndSnapshots
}
