package com.squareup.workflow1

import com.squareup.workflow1.RuntimeConfig.ConflateStaleRenderings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
 * @param workflowRuntimePlugin
 * This is used to plug in Runtime functionality that lives in other modules.
 *
 * @return
 * A [StateFlow] of [RenderingAndSnapshot]s that will emit any time the root workflow creates a new
 * rendering.
 */
@OptIn(ExperimentalCoroutinesApi::class, WorkflowExperimentalRuntime::class)
public fun <PropsT, OutputT, RenderingT> renderWorkflowIn(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  scope: CoroutineScope,
  props: StateFlow<PropsT>,
  initialSnapshot: TreeSnapshot? = null,
  interceptors: List<WorkflowInterceptor> = emptyList(),
  runtimeConfig: RuntimeConfig = RuntimeConfig.DEFAULT_CONFIG,
  workflowRuntimePlugin: WorkflowRuntimePlugin? = null,
  onOutput: suspend (OutputT) -> Unit
): StateFlow<RenderingAndSnapshot<RenderingT>> {
  val chainedInterceptor = workflowRuntimePlugin?.chainedInterceptors(interceptors)
    ?: interceptors.chained()

  val runner = workflowRuntimePlugin?.createWorkflowRunner(
    scope, workflow, props, initialSnapshot, chainedInterceptor, runtimeConfig
  )
    ?: WorkflowRunner(scope, workflow, props, initialSnapshot, chainedInterceptor, runtimeConfig)

  fun firstRenderingFlow(): StateFlow<RenderingAndSnapshot<RenderingT>> =
    MutableStateFlow(
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

  val useComposeInRuntime = workflowRuntimePlugin != null && runtimeConfig.useComposeInRuntime
  // Rendering is synchronous, so we can run the first render pass before launching the runtime
  // coroutine to calculate the initial rendering.
  val renderingsAndSnapshots = if (useComposeInRuntime) {
    require(workflowRuntimePlugin != null) {
      "Cannot use compose without plugging in" +
        " the workflow-compose-core module."
    }
    try {
      workflowRuntimePlugin.initializeRenderingStream(
        runner,
        runtimeScope = scope
      )
    }
    // catch (npe: NullPointerException) {
    //   // See https://android-review.googlesource.com/c/platform/frameworks/support/+/2267995 where
    //   // canceled/completed scope crashes Compose
    //   useComposeInRuntime = false
    //   firstRenderingFlow()
    // }
    catch (e: Throwable) {
      val cancellation =
        (e as? CancellationException) ?: CancellationException("Workflow runtime failed", e)
      runner.cancelRuntime(cancellation)
      throw e
    }
  } else {
    firstRenderingFlow()
  }

  suspend fun <OutputT> sendOutput(
    actionResult: ActionProcessingResult?,
    onOutput: suspend (OutputT) -> Unit
  ) {
    when (actionResult) {
      is WorkflowOutput<*> -> {
        @Suppress("UNCHECKED_CAST")
        (actionResult as? WorkflowOutput<OutputT>)?.let {
          onOutput(it.value)
        }
      }
      else -> {} // no -op
    }
  }

  scope.launch {
    while (isActive) {
      // It might look weird to start by processing an action before getting the rendering below,
      // but remember the first render pass already occurred above, before this coroutine was even
      // launched.
      var actionResult: ActionProcessingResult? = runner.processAction()

      // After resuming from runner.processAction() our coroutine could now be cancelled, check so
      // we don't surprise anyone with an unexpected rendering pass. Show's over, go home.
      if (!isActive) return@launch

      var nextRenderAndSnapshot: RenderingAndSnapshot<RenderingT>? = if (!useComposeInRuntime) {
        runner.nextRendering()
      } else {
        null
      }

      if (runtimeConfig == ConflateStaleRenderings) {
        // Only null will allow us to continue processing actions and conflating stale renderings.
        // If this is not null, then we had an Output and we want to send it with the Rendering
        // (stale or not).
        while (actionResult == null) {
          // We have more actions we can process, so this rendering is stale.
          actionResult = runner.processAction(waitForAnAction = false)

          if (!isActive) return@launch

          // If no actions processed, then no new rendering needed.
          if (actionResult == ActionsExhausted) break

          nextRenderAndSnapshot = runner.nextRendering()
        }
      }

      if (useComposeInRuntime) {
        // TODO (https://github.com/square/workflow-kotlin/issues/835): Figure out how to handle
        // the case where the state changes on the first action as this is broken now.
        // This will wait until the rendering is placed into the stateflow by molecule after it is
        // composed.
        workflowRuntimePlugin?.nextRendering()
      } else {
        // Pass on to the UI.
        (renderingsAndSnapshots as MutableStateFlow).value = nextRenderAndSnapshot!!
      }

      // And emit the Output.
      sendOutput(actionResult, onOutput)
    }
  }

  return renderingsAndSnapshots
}
