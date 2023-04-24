package com.squareup.workflow1.config

import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfig.ConflateStaleRenderings
import com.squareup.workflow1.RuntimeConfig.RenderOnStateChangeOnly
import com.squareup.workflow1.RuntimeConfig.RenderPerAction
import com.squareup.workflow1.WorkflowExperimentalRuntime

public class AndroidRuntimeConfigTools {

  public companion object {
    /**
     * Helper for Configuration for the workflow runtime in an application.
     * This allows one to specify a project property from the gradle build to choose a runtime.
     * e.g. add "-Pworkflow.runtime=timeout" in your gradle build to build the timeout runtime into
     * the application.
     *
     * Note that this must be specified in the application built for any ui/integration tests. Call
     * this function, and then pass that to the call to [renderWorkflowIn] as the [RuntimeConfig].
     *
     * Current options are:
     * "stateChange" : [RenderOnStateChangeOnly] Only re-render when the state of some WorkflowNode
     *      has changed.
     * "conflate" : [ConflateStaleRenderings] Process all queued actions before passing rendering
     *      to the UI layer.
     * "baseline" : [RenderPerAction] Original Workflow Runtime. Note that this doesn't need to
     *      be specified as it is the current default and is assumed by this utility.
     */
    @WorkflowExperimentalRuntime
    public fun getAppWorkflowRuntimeConfig(): RuntimeConfig {
      return when (BuildConfig.WORKFLOW_RUNTIME) {
        "stateChange" -> RenderOnStateChangeOnly
        "conflate" -> ConflateStaleRenderings
        else -> RenderPerAction
      }
    }
  }
}
