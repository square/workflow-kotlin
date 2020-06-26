/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow

import com.squareup.workflow.internal.WorkflowRunner
import com.squareup.workflow.internal.chained
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.ATOMIC
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

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
 * @return
 * A [StateFlow] of [RenderingAndSnapshot]s that will emit any time the root workflow creates a new
 * rendering.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalWorkflowApi::class)
fun <PropsT, OutputT : Any, RenderingT> renderWorkflowIn(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  scope: CoroutineScope,
  props: StateFlow<PropsT>,
  initialSnapshot: TreeSnapshot = TreeSnapshot.NONE,
  interceptors: List<WorkflowInterceptor> = emptyList(),
  workerDispatcher: CoroutineDispatcher? = null,
  onOutput: suspend (OutputT) -> Unit
): StateFlow<RenderingAndSnapshot<RenderingT>> {
  val chainedInterceptor = interceptors.chained()

  val runner =
    WorkflowRunner(
        scope, workflow, props, initialSnapshot, chainedInterceptor,
        workerDispatcher ?: EmptyCoroutineContext
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

  // Launch atomically so the finally block is run even if the scope is cancelled before the
  // coroutine starts executing.
  scope.launch(start = ATOMIC) {
      while (isActive) {
        // It might look weird to start by consuming the output before getting the rendering below,
        // but remember the first render pass already occurred above, before this coroutine was even
        // launched.
        val output = runner.nextOutput()

        // After receiving an output, the next render pass must be done before emitting that output,
        // so that the workflow states appear consistent to observers of the outputs and renderings.
        renderingsAndSnapshots.value = runner.nextRendering()
        output?.let { onOutput(it) }
      }
  }

  return renderingsAndSnapshots
}

/**
 * If this is already a [CancellationException], returns it as-is, otherwise wraps it in one with
 * the given message.
 */
private inline fun Throwable?.toCancellationException(message: () -> String) = when (this) {
  null -> null
  is CancellationException -> this
  else -> CancellationException(message(), this)
}
