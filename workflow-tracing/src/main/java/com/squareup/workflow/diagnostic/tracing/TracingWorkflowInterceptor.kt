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
package com.squareup.workflow.diagnostic.tracing

import com.squareup.tracing.TraceEncoder
import com.squareup.tracing.TraceEvent.AsyncDurationBegin
import com.squareup.tracing.TraceEvent.AsyncDurationEnd
import com.squareup.tracing.TraceEvent.Counter
import com.squareup.tracing.TraceEvent.DurationBegin
import com.squareup.tracing.TraceEvent.DurationEnd
import com.squareup.tracing.TraceEvent.Instant
import com.squareup.tracing.TraceEvent.Instant.InstantScope.GLOBAL
import com.squareup.tracing.TraceEvent.Instant.InstantScope.PROCESS
import com.squareup.tracing.TraceEvent.ObjectCreated
import com.squareup.tracing.TraceEvent.ObjectDestroyed
import com.squareup.tracing.TraceEvent.ObjectSnapshot
import com.squareup.tracing.TraceLogger
import com.squareup.workflow.ExperimentalWorkflowApi
import com.squareup.workflow.RenderContext
import com.squareup.workflow.Sink
import com.squareup.workflow.Snapshot
import com.squareup.workflow.Worker
import com.squareup.workflow.WorkflowAction
import com.squareup.workflow.WorkflowAction.Updater
import com.squareup.workflow.WorkflowInterceptor
import com.squareup.workflow.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow.WorkflowOutput
import com.squareup.workflow.applyTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import okio.buffer
import okio.sink
import java.io.File
import kotlin.LazyThreadSafetyMode.NONE

/**
 * A [WorkflowInterceptor] that generates a trace file that can be viewed in Chrome by
 * visiting `chrome://tracing`.
 *
 * @param file The [File] to write the trace to.
 * @param name If non-empty, will be used to set the "process name" in the trace file. If empty,
 * the workflow type is used for the process name.
 */
@Suppress("FunctionName")
fun TracingWorkflowInterceptor(
  file: File,
  name: String = ""
): TracingWorkflowInterceptor = TracingWorkflowInterceptor(name) { workflowScope ->
  TraceEncoder(workflowScope) {
    file.sink()
        .buffer()
  }
}

/**
 * A [WorkflowInterceptor] that generates a trace file that can be viewed in Chrome by
 * visiting `chrome://tracing`.
 *
 * @param name If non-empty, will be used to set the "process name" in the trace file. If empty,
 * the workflow type is used for the process name.
 * @param encoderProvider A function that returns a [TraceEncoder] that will be used to write trace
 * events. The function gets the [CoroutineScope] that the workflow runtime is running in.
 */
@Suppress("FunctionName")
fun TracingWorkflowInterceptor(
  name: String = "",
  memoryStats: MemoryStats = RuntimeMemoryStats,
  encoderProvider: (workflowScope: CoroutineScope) -> TraceEncoder
): TracingWorkflowInterceptor =
  TracingWorkflowInterceptor(memoryStats = memoryStats) { workflowScope, type ->
    provideLogger(name, workflowScope, type, encoderProvider)
  }

internal fun provideLogger(
  name: String,
  workflowScope: CoroutineScope,
  workflowType: String,
  encoderProvider: (workflowScope: CoroutineScope) -> TraceEncoder
): TraceLogger {
  val encoder = encoderProvider(workflowScope)
  val processName = name.ifEmpty { workflowType }
  return encoder.createLogger(
      processName = processName,
      threadName = "Profiling"
  )
}

/**
 * A [WorkflowInterceptor] that generates a trace file that can be viewed in Chrome by
 * visiting `chrome://tracing`.
 *
 * @constructor The primary constructor is internal so that it can inject [GcDetector] for tests.
 */
@OptIn(ExperimentalWorkflowApi::class)
class TracingWorkflowInterceptor internal constructor(
  private val memoryStats: MemoryStats,
  private val gcDetectorConstructor: GcDetectorConstructor,
  private val loggerProvider: (
    workflowScope: CoroutineScope,
    workflowType: String
  ) -> TraceLogger
) : WorkflowInterceptor {

  /**
   * A [WorkflowInterceptor] that generates a trace file that can be viewed in Chrome by
   * visiting `chrome://tracing`.
   *
   * @param loggerProvider A function that returns a [TraceLogger] that will be used to write trace
   * events. The function gets the [CoroutineScope] that the workflow runtime is running in, as well
   * as a description of the type of the workflow.
   */
  constructor(
    memoryStats: MemoryStats = RuntimeMemoryStats,
    loggerProvider: (
      workflowScope: CoroutineScope,
      workflowType: String
    ) -> TraceLogger
  ) : this(memoryStats, ::GcDetector, loggerProvider)

  /**
   * [NONE] is fine here because it will get initialized by [onRuntimeStarted] and there's no
   * race conditions.
   */
  private var logger: TraceLogger? = null
  private var gcDetector: GcDetector? = null

  private val workflowNamesById = mutableMapOf<Long, String>()
  private val workerDescriptionsById = mutableMapOf<Long, String>()

  private var workerIdCounter = 1

  /** Used to look up workers to perform doesSameWorkAs checks until Workers go away. */
  private val workersById = mutableMapOf<Long, Worker<*>>()

  override fun onSessionStarted(
    workflowScope: CoroutineScope,
    session: WorkflowSession
  ) {
    val workflowJob = workflowScope.coroutineContext[Job]!!

    // Invoke this before runtime logic since cancellation handlers are invoked in the same order
    // in which they were registered, and we want to emit workflow stopped before runtime stopped.
    workflowJob.invokeOnCompletion {
      onWorkflowStopped(session.sessionId)
    }

    if (session.parent == null) {
      onRuntimeStarted(workflowScope, session.identifier.toString())
      workflowJob.invokeOnCompletion {
        onRuntimeStopped()
      }
    }
  }

  override fun <P, S> onInitialState(
    props: P,
    snapshot: Snapshot?,
    proceed: (P, Snapshot?) -> S,
    session: WorkflowSession
  ): S {
    val initialState = proceed(props, snapshot)

    onWorkflowStarted(
        workflowId = session.sessionId,
        parentId = session.parent?.sessionId,
        workflowType = session.identifier.toString(),
        key = session.renderKey,
        initialProps = props,
        initialState = initialState,
        restoredFromSnapshot = snapshot != null
    )

    return initialState
  }

  override fun <P, S> onPropsChanged(
    old: P,
    new: P,
    state: S,
    proceed: (P, P, S) -> S,
    session: WorkflowSession
  ): S {
    val newState = proceed(old, new, state)
    if (session.parent == null) {
      // Fake getting the props changed event from the runtime directly.
      onPropsChanged(
          workflowId = null,
          oldProps = old,
          newProps = new,
          oldState = state,
          newState = newState
      )
    }
    onPropsChanged(
        workflowId = session.sessionId,
        oldProps = old,
        newProps = new,
        oldState = state,
        newState = newState
    )
    return newState
  }

  override fun <P, S, O, R> onRender(
    props: P,
    state: S,
    context: RenderContext<S, O>,
    proceed: (P, S, RenderContext<S, O>) -> R,
    session: WorkflowSession
  ): R {
    if (session.parent == null) {
      // Track the overall render pass for the whole tree.
      onBeforeRenderPass(props)
    }
    onBeforeWorkflowRendered(session.sessionId, props, state)

    val tracingContext = TracingRenderContext(context, session)
    val rendering = proceed(props, state, tracingContext)

    onAfterWorkflowRendered(session.sessionId, rendering)
    if (session.parent == null) {
      onAfterRenderPass(rendering)
    }
    return rendering
  }

  override fun <S> onSnapshotState(
    state: S,
    proceed: (S) -> Snapshot,
    session: WorkflowSession
  ): Snapshot {
    if (session.parent == null) {
      onBeforeSnapshotPass()
    }

    val snapshot = proceed(state)

    if (session.parent == null) {
      onAfterSnapshotPass()
    }
    return snapshot
  }

  private fun onRuntimeStarted(
    workflowScope: CoroutineScope,
    rootWorkflowType: String
  ) {
    logger = loggerProvider(workflowScope, rootWorkflowType)

    // Log garbage collections in case they correlate with unusually long render times.
    gcDetector = gcDetectorConstructor {
      logger?.log(
          listOf(
              Instant(
                  name = "GC detected",
                  scope = GLOBAL,
                  category = "system",
                  args = mapOf(
                      "freeMemory" to memoryStats.freeMemory(),
                      "totalMemory" to memoryStats.totalMemory()
                  )
              ),
              createMemoryEvent()
          )
      )
    }
  }

  private fun onRuntimeStopped() {
    gcDetector?.stop()
  }

  private fun onBeforeRenderPass(props: Any?) {
    logger?.log(
        listOf(
            DurationBegin(
                name = "Render Pass",
                category = "rendering",
                args = mapOf("props" to props.toString())
            ),
            createMemoryEvent()
        )
    )
  }

  private fun onAfterRenderPass(rendering: Any?) {
    logger?.log(
        listOf(
            DurationEnd(
                name = "Render Pass",
                category = "rendering",
                args = mapOf("rendering" to rendering.toString())
            ),
            createMemoryEvent()
        )
    )
  }

  private fun onWorkflowStarted(
    workflowId: Long,
    parentId: Long?,
    workflowType: String,
    key: String,
    initialProps: Any?,
    initialState: Any?,
    restoredFromSnapshot: Boolean
  ) {
    val keyPart = if (key.isEmpty()) "" else ":$key"
    val name = "$workflowType$keyPart (${workflowId.toHex()})"
    workflowNamesById[workflowId] = name
    logger?.log(
        listOf(
            AsyncDurationBegin(
                id = "workflow",
                name = name,
                category = "workflow",
                args = mapOf(
                    "workflowId" to workflowId.toHex(),
                    "initialProps" to initialProps.toString(),
                    "initialState" to initialState.toString(),
                    "restoredFromSnapshot" to restoredFromSnapshot,
                    "parent" to workflowNamesById[parentId]
                )
            ),
            ObjectCreated(
                id = workflowId,
                objectType = name
            )
        )
    )
  }

  private fun onWorkflowStopped(workflowId: Long) {
    val name = workflowNamesById.getValue(workflowId)
    logger?.log(
        listOf(
            AsyncDurationEnd(
                id = "workflow",
                name = name,
                category = "workflow"
            ),
            ObjectDestroyed(
                id = workflowId,
                objectType = name
            )
        )
    )
    workflowNamesById -= workflowId
  }

  private fun onWorkerStarted(
    workerId: Long,
    parentWorkflowId: Long,
    key: String,
    description: String
  ) {
    val parentName = workflowNamesById.getValue(parentWorkflowId)
    workerDescriptionsById[workerId] = description
    logger?.log(
        AsyncDurationBegin(
            id = "workflow",
            name = "Worker: ${workerId.toHex()}",
            category = "workflow",
            args = mapOf(
                "parent" to parentName,
                "key" to key,
                "description" to description
            )
        )
    )
  }

  private fun onWorkerStopped(workerId: Long) {
    val description = workerDescriptionsById.getValue(workerId)
    logger?.log(
        AsyncDurationEnd(
            id = "workflow",
            name = "Worker: ${workerId.toHex()}",
            category = "workflow",
            args = mapOf("description" to description)
        )
    )
    workerDescriptionsById -= workerId
  }

  private fun onBeforeWorkflowRendered(
    workflowId: Long,
    props: Any?,
    state: Any?
  ) {
    val name = workflowNamesById.getValue(workflowId)
    logger?.log(
        DurationBegin(
            name,
            args = mapOf(
                "workflowId" to workflowId.toHex(),
                "props" to props.toString(),
                "state" to state.toString()
            ),
            category = "rendering"
        )
    )
  }

  private fun onAfterWorkflowRendered(
    workflowId: Long,
    rendering: Any?
  ) {
    val name = workflowNamesById.getValue(workflowId)
    logger?.log(
        DurationEnd(
            name,
            args = mapOf("rendering" to rendering.toString()),
            category = "rendering"
        )
    )
  }

  private fun onBeforeSnapshotPass() {
    logger?.log(DurationBegin(name = "Snapshot"))
  }

  private fun onAfterSnapshotPass() {
    logger?.log(DurationEnd(name = "Snapshot"))
  }

  private fun onSinkReceived(
    workflowId: Long,
    action: WorkflowAction<*, *>
  ) {
    val name = workflowNamesById.getValue(workflowId)
    logger?.log(
        Instant(
            name = "Sink received: $name",
            category = "update",
            args = mapOf("action" to action.toString())
        )
    )
  }

  private fun onWorkerOutput(
    workerId: Long,
    parentWorkflowId: Long,
    output: Any
  ) {
    val parentName = workflowNamesById.getValue(parentWorkflowId)
    val description = workerDescriptionsById.getValue(workerId)
    logger?.log(
        Instant(
            name = "Worker output: $parentName",
            category = "update",
            args = mapOf(
                "workerId" to workerId.toHex(),
                "description" to description,
                "output" to output.toString()
            )
        )
    )
  }

  private fun onPropsChanged(
    workflowId: Long?,
    oldProps: Any?,
    newProps: Any?,
    oldState: Any?,
    newState: Any?
  ) {
    val name = workflowNamesById[workflowId] ?: "{root}"
    logger?.log(
        Instant(
            name = "Props changed: $name",
            args = mapOf(
                "oldProps" to oldProps.toString(),
                "newProps" to if (oldProps == newProps) "{no change}" else newProps.toString(),
                "oldState" to oldState.toString(),
                "newState" to if (oldState == newState) "{no change}" else newState.toString()
            )
        )
    )
  }

  private fun onWorkflowAction(
    workflowId: Long,
    action: WorkflowAction<*, *>,
    oldState: Any?,
    newState: Any?,
    output: WorkflowOutput<Any?>?
  ) {
    val name = workflowNamesById.getValue(workflowId)

    logger?.log(
        listOf(
            Instant(
                name = "WorkflowAction: $name",
                category = "update",
                scope = PROCESS,
                args = mapOf(
                    "action" to action.toString(),
                    "oldState" to oldState.toString(),
                    "newState" to if (oldState == newState) "{no change}" else newState.toString(),
                    "output" to (output?.let { it.value.toString() } ?: "{no output}")
                )
            ),
            ObjectSnapshot(
                id = workflowId,
                objectType = name,
                snapshot = newState.toString()
            )
        )
    )
  }

  private fun createMemoryEvent(): Counter {
    val freeMemory = memoryStats.freeMemory()
    val usedMemory = memoryStats.totalMemory() - freeMemory
    return Counter(
        name = "used/free memory",
        series = mapOf(
            // This map is ordered. The stacked chart is shown in reverse order so it looks like a
            // typical memory usage graph.
            "usedMemory" to usedMemory,
            "freeMemory" to freeMemory
        )
    )
  }

  /**
   * Creates a unique ID for a worker by incrementing the worker counter int, then storing the
   * previous counter value in the most-significant half of the workflow ID's bits (since those
   * bits are unused by workflow IDs in the real world).
   */
  private fun createWorkerId(session: WorkflowSession) =
    workerIdCounter++.toLong()
        .shl(32) xor session.sessionId

  private inner class TracingRenderContext<S, O>(
    private val delegate: RenderContext<S, O>,
    private val session: WorkflowSession
  ) : RenderContext<S, O> by delegate, Sink<WorkflowAction<S, O>> {
    override val actionSink: Sink<WorkflowAction<S, O>> get() = this

    override fun send(value: WorkflowAction<S, O>) {
      onSinkReceived(session.sessionId, value)
      val wrapperAction = TracingAction(value, session)
      delegate.actionSink.send(wrapperAction)
    }

    override fun <T> runningWorker(
      worker: Worker<T>,
      key: String,
      handler: (T) -> WorkflowAction<S, O>
    ) {
      val wrappedWorker = TracingWorker(session, worker, key)
      delegate.runningWorker(wrappedWorker, key) { output ->
        TracingAction(handler(output), session)
      }
    }
  }

  private inner class TracingAction<S, O>(
    private val delegate: WorkflowAction<S, O>,
    private val session: WorkflowSession
  ) : WorkflowAction<S, O> {
    override fun Updater<S, O>.apply() {
      val oldState = nextState
      val (newState, output) = delegate.applyTo(nextState)
      nextState = newState
      output?.let { setOutput(it.value) }
      onWorkflowAction(
          workflowId = session.sessionId,
          action = delegate,
          oldState = oldState,
          newState = newState,
          output = output
      )
    }
  }

  private inner class TracingWorker<OutputT>(
    private val session: WorkflowSession,
    private val delegate: Worker<OutputT>,
    private val key: String
  ) : Worker<OutputT> {

    val workerId: Long

    init {
      val existingWorker = workersById.filterValues { it.doesSameWorkAs(this) }
          .entries
          .singleOrNull()
      workerId = existingWorker?.key ?: (createWorkerId(session).also { workerId ->
        onWorkerStarted(workerId, session.sessionId, key, delegate.toString())
        workersById[workerId] = this
      })
    }

    override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean =
      otherWorker is TracingWorker<*> &&
          session == otherWorker.session &&
          delegate.doesSameWorkAs(otherWorker.delegate)

    override fun run(): Flow<OutputT> = flow {
      try {
        delegate.run()
            .collect { output ->
              onWorkerOutput(workerId, session.sessionId, output ?: "{null}")
              emit(output)
            }
      } finally {
        onWorkerStopped(workerId)
      }
    }
  }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Long.toHex() = toString(16)
