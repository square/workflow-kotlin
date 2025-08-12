package com.squareup.workflow1.tracing

import kotlin.time.Duration

/**
 * Can be passed to a [WorkflowRuntimeMonitor] to track each render pass as it happens, and the
 * cause of it.
 */
public fun interface WorkflowRenderPassTracker {

  /**
   * Records that a render pass happened.
   */
  public fun recordRenderPass(renderPass: RenderPassInfo)
}

/**
 * A bundle of little info about a render pass.
 */
public class RenderPassInfo(
  public val runnerName: String,
  public val renderCause: RenderCause,
  public val durationUptime: Duration
)

/**
 * Explanation of what caused a render pass. [toString] implementations
 * provide a concise, Perfetto-friendly description with key:
 *
 * - A(action name)
 * - R(worker name)
 * - W(workflow name)
 */
public sealed interface RenderCause {
  /**
   * The props passed into the runtime have changed.
   */
  public object RootPropsChanged : RenderCause {
    override fun toString() = "Root Props changed"
  }

  /**
   * First creation of the root workflow for the runtime.
   */
  public class RootCreation(
    public val runnerName: String,
    public val workflowName: String,
  ) : RenderCause {
    override fun toString(): String {
      return "Creation of $runnerName root workflow $workflowName"
    }
  }

  /**
   * An action was handled.
   */
  public class Action(
    public val actionName: String,
    public val workerIncomingName: String?,
    public val workflowName: String,
  ) : RenderCause {
    override fun toString(): String {
      return "Output:A($actionName)/R($workerIncomingName)/W($workflowName)"
    }
  }

  /**
   * A worker's wrapping workflow is waiting to receive the output.
   * This should not ever be the only cause of a render pass.
   */
  public class WaitingForOutput(val workflowName: String) : RenderCause {
    override fun toString(): String {
      return "W($workflowName)/WaitingForOutput"
    }
  }

  /**
   * A rendering callback was invoked.
   */
  public class Callback(
    public val actionName: String,
    public val workflowName: String,
  ) : RenderCause {
    override fun toString(): String {
      return "Callback:A($actionName)/W($workflowName)"
    }
  }
}
