package com.squareup.workflow1.tracing

import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.tracing.ActionAppliedLogLine.WorkflowActionLogType.CASCADE
import com.squareup.workflow1.tracing.ActionAppliedLogLine.WorkflowActionLogType.RENDERING_CALLBACK
import com.squareup.workflow1.tracing.ActionAppliedLogLine.WorkflowActionLogType.WORKER_OUTPUT

/**
 * PLEASE NOTE: these log lines are turned into strings in production, all the time, and there
 * are many of them.
 * So we take good care of keeping this code tight and performant, by leveraging StringBuilder and
 * not doing any Kotlin string interpolation (which creates intermediate builder objects) as well
 * as preferring the Java builder methods over the kotlin extension functions (e.g. append('\n')
 * instead of appendLine()): even though the Kotlin extension functions are inlined, they also
 * strengthen the types from the Java compatibility "maybe nullable" to "definitely not null" and
 * the compiler adds 2 call to Intrinsics.checkNotNullExpressionValue per use (for the callee and
 * the result, both of which are StringBuilder!).
 */
public sealed interface RuntimeUpdateLogLine {
  fun log(builder: StringBuilder)
}

internal const val MAX_LOG_FIELD_LENGTH: Int = 1024

internal fun RuntimeUpdateLogLine.withLogLimits(
  maxLogLineLength: Int,
  crashOnLogLineOverflow: Boolean,
): RuntimeUpdateLogLine {
  require(maxLogLineLength > 0)

  return when (this) {
    is UiUpdateLogLine -> copyWithLogLimits(maxLogLineLength, crashOnLogLineOverflow)
    is ActionAppliedLogLine -> copyWithLogLimits(maxLogLineLength, crashOnLogLineOverflow)
    is ActionDroppedLogLine -> copyWithLogLimits(maxLogLineLength, crashOnLogLineOverflow)
    is StaleWorkerOutputLogLine -> copyWithLogLimits(maxLogLineLength, crashOnLogLineOverflow)
    RenderLogLine,
    SkipLogLine,
    -> this
  }
}

private data class LogLineLimits(
  val maxLogLineLength: Int,
  val crashOnLogLineOverflow: Boolean,
)

private val RuntimeUpdateLogLine.logLineType: String
  get() = this::class.toWfLoggingName()

private inline fun RuntimeUpdateLogLine.logWithLimits(
  builder: StringBuilder,
  logLineLimits: LogLineLimits?,
  block: () -> Unit,
) {
  if (logLineLimits == null) {
    block()
    return
  }

  val lineStart = builder.length
  block()
  builder.enforceLogLineLimit(
    lineStart = lineStart,
    maxLogLineLength = logLineLimits.maxLogLineLength,
    crashOnLogLineOverflow = logLineLimits.crashOnLogLineOverflow,
    logLineType = logLineType
  )
}

private fun StringBuilder.enforceLogLineLimit(
  lineStart: Int,
  maxLogLineLength: Int,
  crashOnLogLineOverflow: Boolean,
  logLineType: String,
) {
  val actualLength = length - lineStart
  if (maxLogLineLength == Int.MAX_VALUE || actualLength <= maxLogLineLength) return

  if (crashOnLogLineOverflow) {
    setLength(lineStart)
    throw logLineOverflowException(
      logLineType = logLineType,
      actualLength = actualLength.toLong(),
      maxLogLineLength = maxLogLineLength
    )
  }
  wfEllipsizeEndInPlacePreservingFinalNewline(
    startIndex = lineStart,
    maxLength = maxLogLineLength
  )
}

private fun logLineOverflowException(
  logLineType: String,
  actualLength: Long,
  maxLogLineLength: Int,
): IllegalStateException {
  return IllegalStateException(
    "$logLineType exceeded maxLogLineLength=$maxLogLineLength with actualLength=$actualLength."
  )
}

/**
 * The "UI" has updated, whatever that means for your app.
 * You must manually add this to the [WorkflowRuntimeMonitor]'s [RuntimeUpdates] by calling
 * [WorkflowRuntimeMonitor.addRuntimeUpdate].
 */
public class UiUpdateLogLine private constructor(
  val note: String,
  private val logLineLimits: LogLineLimits?,
) : RuntimeUpdateLogLine {
  public constructor(note: String) : this(
    note = note,
    logLineLimits = null
  )

  internal fun copyWithLogLimits(
    maxLogLineLength: Int,
    crashOnLogLineOverflow: Boolean,
  ): UiUpdateLogLine = UiUpdateLogLine(
    note = note,
    logLineLimits = LogLineLimits(maxLogLineLength, crashOnLogLineOverflow)
  )

  override fun log(builder: StringBuilder) {
    logWithLimits(builder, logLineLimits) {
      builder
        .append("UI UPDATE: ")
        .append(note)
        .append('\n')
    }
  }
}

/**
 * The Workflow runtime has executed a render pass.
 */
public data object RenderLogLine : RuntimeUpdateLogLine {
  override fun log(builder: StringBuilder) {
    builder
      .append("RENDERED")
      .append('\n')
  }
}

/**
 * The Workflow runtime has skipped a render pass.
 */
public data object SkipLogLine : RuntimeUpdateLogLine {
  override fun log(builder: StringBuilder) {
    builder
      .append("SKIP RENDER")
      .append('\n')
  }
}

/**
 * An action that was queued but got dropped because the Workflow session ended.
 */
public class ActionDroppedLogLine private constructor(
  val actionName: String,
  private val logLineLimits: LogLineLimits?,
) : RuntimeUpdateLogLine {
  public constructor(actionName: String) : this(
    actionName = actionName,
    logLineLimits = null
  )

  internal fun copyWithLogLimits(
    maxLogLineLength: Int,
    crashOnLogLineOverflow: Boolean,
  ): ActionDroppedLogLine = ActionDroppedLogLine(
    actionName = actionName,
    logLineLimits = LogLineLimits(maxLogLineLength, crashOnLogLineOverflow)
  )

  override fun log(builder: StringBuilder) {
    logWithLimits(builder, logLineLimits) {
      builder.append("DROPPED: ")
        .append(actionName)
        .append('\n')
    }
  }
}

/**
 * The monitor saw a worker output action but did not see the corresponding output handler action
 * before another runtime boundary was reached.
 */
internal class StaleWorkerOutputLogLine private constructor(
  val pendingWorkerName: String,
  val detectionPoint: String,
  val nextActionName: String?,
  val nextWorkflowName: String?,
  val renderIncomingCauses: List<RenderCause>,
  val previousRenderCause: RenderCause?,
  private val logLineLimits: LogLineLimits?,
) : RuntimeUpdateLogLine {
  constructor(
    pendingWorkerName: String,
    detectionPoint: String,
    nextActionName: String?,
    nextWorkflowName: String?,
    renderIncomingCauses: List<RenderCause>,
    previousRenderCause: RenderCause?,
  ) : this(
    pendingWorkerName = pendingWorkerName,
    detectionPoint = detectionPoint,
    nextActionName = nextActionName,
    nextWorkflowName = nextWorkflowName,
    renderIncomingCauses = renderIncomingCauses,
    previousRenderCause = previousRenderCause,
    logLineLimits = null
  )

  internal fun copyWithLogLimits(
    maxLogLineLength: Int,
    crashOnLogLineOverflow: Boolean,
  ): StaleWorkerOutputLogLine = StaleWorkerOutputLogLine(
    pendingWorkerName = pendingWorkerName,
    detectionPoint = detectionPoint,
    nextActionName = nextActionName,
    nextWorkflowName = nextWorkflowName,
    renderIncomingCauses = renderIncomingCauses,
    previousRenderCause = previousRenderCause,
    logLineLimits = LogLineLimits(maxLogLineLength, crashOnLogLineOverflow)
  )

  override fun log(builder: StringBuilder) {
    logWithLimits(builder, logLineLimits) {
      builder
        .append("STALE WORKER OUTPUT: R(")
        .append(pendingWorkerName)
        .append(") at ")
        .append(detectionPoint)

      if (nextActionName != null || nextWorkflowName != null) {
        builder.append(" before ")
        nextActionName?.let {
          builder
            .append("A(")
            .append(it)
            .append(")")
        }
        nextWorkflowName?.let {
          builder
            .append("/W(")
            .append(it)
            .append(")")
        }
      }
      builder.append('\n')

      builder.append("  renderIncomingCauses = ")
      if (renderIncomingCauses.isEmpty()) {
        builder.append("(empty)")
      } else {
        renderIncomingCauses.forEachIndexed { index, cause ->
          if (index > 0) builder.append(", ")
          builder.append(cause)
        }
      }
      builder.append('\n')

      builder
        .append("  previousRenderCause = ")
        .append(previousRenderCause ?: "(none)")
        .append('\n')
    }
  }
}

/**
 * The Workflow runtime has applied an action.
 */
public class ActionAppliedLogLine private constructor(
  val type: WorkflowActionLogType,
  val name: String,
  val actionName: String,
  val propsOrNull: Any?,
  val oldState: Any?,
  val newState: Any?,
  val outputOrNull: WorkflowOutput<*>?,
  val outputReceivedString: String?,
  private val logLineLimits: LogLineLimits?,
) : RuntimeUpdateLogLine {
  public constructor(
    type: WorkflowActionLogType,
    name: String,
    actionName: String,
    propsOrNull: Any?,
    oldState: Any?,
    newState: Any?,
    outputOrNull: WorkflowOutput<*>?,
    outputReceivedString: String?,
  ) : this(
    type = type,
    name = name,
    actionName = actionName,
    propsOrNull = propsOrNull,
    oldState = oldState,
    newState = newState,
    outputOrNull = outputOrNull,
    outputReceivedString = outputReceivedString,
    logLineLimits = null
  )

  internal fun copyWithLogLimits(
    maxLogLineLength: Int,
    crashOnLogLineOverflow: Boolean,
  ): ActionAppliedLogLine = ActionAppliedLogLine(
    type = type,
    name = name,
    actionName = actionName,
    propsOrNull = propsOrNull,
    oldState = oldState,
    newState = newState,
    outputOrNull = outputOrNull,
    outputReceivedString = outputReceivedString,
    logLineLimits = LogLineLimits(maxLogLineLength, crashOnLogLineOverflow)
  )

  public enum class WorkflowActionLogType {
    RENDERING_CALLBACK,
    WORKER_OUTPUT,
    CASCADE,
  }

  override fun log(builder: StringBuilder) {
    logWithLimits(builder, logLineLimits) {
      when (type) {
        RENDERING_CALLBACK -> builder.append("Rendering Callback: ")
        WORKER_OUTPUT -> builder.append("Worker Output: ")
        CASCADE -> builder.append("Cascade: ")
      }
      if (outputReceivedString != null) {
        builder.append(outputReceivedString.wfEllipsizeEnd(MAX_LOG_FIELD_LENGTH))
        builder.append(": ")
      }
      if (actionName.isNotBlank()) {
        builder.append("A(")
        builder.append(actionName)
        builder.append(")")
        builder.append("/")
      }
      builder.append(name)
      builder.append(": ")
      if (outputOrNull != null) {
        builder.appendWfLogString(outputOrNull.value, MAX_LOG_FIELD_LENGTH)
      } else {
        builder.append("(no output)")
      }
      builder.append('\n')

      propsOrNull?.let {
        builder.append("  props = ")
        builder.appendWfLogString(it, MAX_LOG_FIELD_LENGTH)
        builder.append('\n')
      }

      if (oldState != newState) {
        builder.append("  oldState = ")
        builder.appendWfLogString(oldState, MAX_LOG_FIELD_LENGTH)
        builder.append('\n')
        builder.append("  newState = ")
        builder.appendWfLogString(newState, MAX_LOG_FIELD_LENGTH)
        builder.append('\n')
      } else {
        builder.append("  state = ")
        builder.appendWfLogString(oldState, MAX_LOG_FIELD_LENGTH)
        builder.append('\n')
      }
    }
  }
}
