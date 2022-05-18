package com.squareup.workflow1.config

import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfig.FrameTimeout
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
     * "timeout" : [FrameTimeout] Process Multiple Actions w/ a Frame Timeout.
     * "baseline" : [RenderPerAction] Original Workflow Runtime. Note that this doesn't need to
     *      be specified as it is the current default and is assumed by this utility.
     */
    @WorkflowExperimentalRuntime
    public fun getAppWorkflowRuntimeConfig(): RuntimeConfig {
      return when (BuildConfig.WORKFLOW_RUNTIME) {
        "timeout" -> FrameTimeout()
        else -> RenderPerAction
      }
    }
  }
}
