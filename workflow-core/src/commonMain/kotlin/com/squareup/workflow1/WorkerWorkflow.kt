@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.reflect.KType

/**
 * The [Workflow] that implements the logic for actually running [Worker]s.
 *
 * This workflow is an [ImpostorWorkflow] and uses the entire [KType] of the [Worker] as its
 * [realIdentifier], so that the runtime can ensure that distinct worker types are allowed to run
 * concurrently. Implements [Worker.doesSameWorkAs] by taking the actual worker instance as its
 * props, and checking [Worker.doesSameWorkAs] in [onPropsChanged]. When this returns false, it
 * means a new worker session needs to be started, and that is achieved by storing a monotonically-
 * increasing integer as the state, and incrementing it whenever the worker needs to be restarted.
 *
 * Note that since this workflow uses an [unsnapshottableIdentifier] as its [realIdentifier], it is
 * not snapshottable, but that's fine because the only state this workflow maintains is only used
 * to determine whether to restart workers during the lifetime of a single runtime instance.
 *
 * @param workerType The [KType] representing the particular type of `Worker<OutputT>`.
 * @param key The key used to render this workflow, as passed to
 * [BaseRenderContext.runningWorker]. Used for naming the worker's coroutine.
 */
internal class WorkerWorkflow<OutputT>(
  val workerType: KType,
  private val key: String,
  workflowTracer: WorkflowTracer? = null
) : StatefulWorkflow<Worker<OutputT>, Int, OutputT, Unit>(),
  ImpostorWorkflow {

  override val realIdentifier: WorkflowIdentifier =
    workflowTracer.trace("ComputeRealIdentifier") {
      unsnapshottableIdentifier(workerType)
    }

  override fun describeRealIdentifier(): String =
    workerType.toString().replace(" (Kotlin reflection is not available)", "")

  override fun initialState(
    props: Worker<OutputT>,
    snapshot: Snapshot?
  ): Int = 0

  override fun onPropsChanged(
    old: Worker<OutputT>,
    new: Worker<OutputT>,
    state: Int
  ): Int = if (!old.doesSameWorkAs(new)) state + 1 else state

  override fun render(
    renderProps: Worker<OutputT>,
    renderState: Int,
    context: RenderContext<Worker<OutputT>, Int, OutputT>
  ) {
    val localKey = renderState.toString()
    // Scope the side effect coroutine to the state value, so the worker will be re-started when
    // it changes (such that doesSameWorkAs returns false above).
    context.runningSideEffect(localKey) {
      runWorker(renderProps, key, context.actionSink)
    }
  }

  override fun snapshotState(state: Int): Snapshot? = null
}

/**
 * Does the actual running of a worker passed to [BaseRenderContext.runningWorker] by setting up the
 * coroutine environment for the worker, performing some validation, etc., and finally actually
 * collecting the worker's [Flow].
 *
 * Visible for testing.
 */
internal suspend fun <OutputT> runWorker(
  worker: Worker<OutputT>,
  renderKey: String,
  actionSink: Sink<WorkflowAction<Worker<OutputT>, Int, OutputT>>
) {
  withContext(CoroutineName(worker.debugName(renderKey))) {
    worker.runWithNullCheck()
      .collectToSink(actionSink) { output ->
        EmitWorkerOutputAction(worker, renderKey, output)
      }
  }
}

private class EmitWorkerOutputAction<P, S, O>(
  worker: Worker<*>,
  renderKey: String,
  private val output: O,
) : WorkflowAction<P, S, O>() {
  override val debuggingName: String =
    "EmitWorkerOutputAction(worker=$worker, key=$renderKey)"

  override fun Updater.apply() {
    setOutput(output)
  }
}

/**
 * In unit tests, if you use a mocking library to create a Worker, the run method will return null
 * even though the return type is non-nullable in Kotlin. Kotlin helps out with this by throwing an
 * NPE before before any kotlin code gets the null, but the NPE that it throws includes an almost
 * completely useless stacktrace and no other details.
 *
 * This method does an explicit null check and throws an exception with a more helpful message.
 *
 * See [#842](https://github.com/square/workflow/issues/842).
 */
@Suppress("USELESS_ELVIS")
private fun <T> Worker<T>.runWithNullCheck(): Flow<T> =
  run() ?: throw NullPointerException(
    "Worker $this returned a null Flow. " +
      "If this is a test mock, make sure you mock the run() method!"
  )

private fun Worker<*>.debugName(key: String) =
  toString().let { if (key.isBlank()) it else "$it:$key" }
