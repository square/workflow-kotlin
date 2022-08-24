package com.squareup.workflow1.config

import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfig.ConflateStaleRenderings
import com.squareup.workflow1.RuntimeConfig.RenderPerAction
import com.squareup.workflow1.WorkflowExperimentalRuntime

public class JvmTestRuntimeConfigTools {
  public companion object {
    /**
     * Helper for Configuration for the Workflow Runtime while running tests on the JVM.
     * This allows one to specify a project property from the gradle build to choose a runtime.
     * e.g. add "-Pworkflow.runtime=timeout" in your gradle build to build the timeout runtime.
     *
     * The [WorkflowTestRuntime] already calls this utility, but if starting your own runtime, then
     * call this function and pass the result to the call to [renderWorkflowIn] as the
     * [RuntimeConfig].
     *
     * Current options are:
     * "conflate" : [ConflateStaleRenderings] Process all queued actions before passing rendering
     *      to the UI layer.
     * "baseline" : [RenderPerAction] Original Workflow Runtime. Note that this doesn't need to
     *      be specified as it is the current default and is assumed by this utility.
     */
    @OptIn(WorkflowExperimentalRuntime::class)
    public fun getTestRuntimeConfig(): RuntimeConfig {
      val runtimeConfig = System.getProperty("workflow.runtime", "baseline")
      return when (runtimeConfig) {
        "conflate" -> ConflateStaleRenderings
        else -> RenderPerAction
      }
    }
  }
}
