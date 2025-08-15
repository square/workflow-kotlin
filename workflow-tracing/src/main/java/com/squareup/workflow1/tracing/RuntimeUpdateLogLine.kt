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

/**
 * The "UI" has updated, whatever that means for your app.
 * You must manually add this to the [WorkflowRuntimeMonitor]'s [RuntimeUpdates] by calling
 * [WorkflowRuntimeMonitor.addRuntimeUpdate].
 */
public class UiUpdateLogLine(
  val note: String
) : RuntimeUpdateLogLine {
  override fun log(builder: StringBuilder) {
    builder
      .append("UI UPDATE: ")
      .append(note)
      .append('\n')
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
 * The Workflow runtime has applied an action.
 */
public class ActionAppliedLogLine(
  val type: WorkflowActionLogType,
  val name: String,
  val actionName: String,
  val propsOrNull: Any?,
  val oldState: Any?,
  val newState: Any?,
  val outputOrNull: WorkflowOutput<*>?,
  val outputReceivedString: String?,
) : RuntimeUpdateLogLine {

  public enum class WorkflowActionLogType {
    RENDERING_CALLBACK,
    WORKER_OUTPUT,
    CASCADE,
  }

  override fun log(builder: StringBuilder) {
    val lineBuilder = StringBuilder().apply {
      when (type) {
        RENDERING_CALLBACK -> append("Rendering Callback: ")
        WORKER_OUTPUT -> append("Worker Output: ")
        CASCADE -> append("Cascade: ")
      }
      if (outputReceivedString != null) {
        append(outputReceivedString)
        append(": ")
      }
      if (actionName.isNotBlank()) {
        append("A(")
        append(actionName)
        append(")")
        append("/")
      }
      append(name)
      append(": ")
      append(
        if (outputOrNull != null) {
          getWfLogString(outputOrNull.value)
        } else {
          "(no output)"
        }
      )
      append('\n')

      propsOrNull?.let {
        append("  props = ")
        append(getWfLogString(it))
        append('\n')
      }

      if (oldState != newState) {
        append("  oldState = ")
        append(getWfLogString(oldState))
        append('\n')
        append("  newState = ")
        append(getWfLogString(newState))
        append('\n')
      } else {
        append("  state = ")
        append(getWfLogString(oldState))
        append('\n')
      }
    }

    builder.append(lineBuilder)
  }
}
