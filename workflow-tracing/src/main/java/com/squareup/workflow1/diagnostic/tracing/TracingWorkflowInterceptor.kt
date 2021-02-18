package com.squareup.workflow1.diagnostic.tracing

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
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.applyTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
public fun TracingWorkflowInterceptor(
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
public fun TracingWorkflowInterceptor(
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
public class TracingWorkflowInterceptor internal constructor(
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
  public constructor(
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
    renderProps: P,
    renderState: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
    session: WorkflowSession
  ): R {
    if (session.parent == null) {
      // Track the overall render pass for the whole tree.
      onBeforeRenderPass(renderProps)
    }
    onBeforeWorkflowRendered(session.sessionId, renderProps, renderState)

    val rendering = proceed(renderProps, renderState, TracingContextInterceptor(session))

    onAfterWorkflowRendered(session.sessionId, rendering)
    if (session.parent == null) {
      onAfterRenderPass(rendering)
    }
    return rendering
  }

  override fun <S> onSnapshotState(
    state: S,
    proceed: (S) -> Snapshot?,
    session: WorkflowSession
  ): Snapshot? {
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
    action: WorkflowAction<*, *, *>
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
    action: WorkflowAction<*, *, *>,
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

  private inner class TracingContextInterceptor<P, S, O>(
    private val session: WorkflowSession
  ) : RenderContextInterceptor<P, S, O> {
    override fun onActionSent(
      action: WorkflowAction<P, S, O>,
      proceed: (WorkflowAction<P, S, O>) -> Unit
    ) {
      onSinkReceived(session.sessionId, action)
      val wrapperAction = TracingAction(action, session)
      proceed(wrapperAction)
    }
  }

  private inner class TracingAction<P, S, O>(
    private val delegate: WorkflowAction<P, S, O>,
    private val session: WorkflowSession
  ) : WorkflowAction<P, S, O>() {
    override fun Updater.apply() {
      val oldState = state
      val (newState, output) = delegate.applyTo(props, state)
      state = newState
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
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Long.toHex() = toString(16)
