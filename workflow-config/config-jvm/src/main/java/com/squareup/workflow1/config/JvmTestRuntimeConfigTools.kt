package com.squareup.workflow1.config

import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfig.RenderPassPerAction
import com.squareup.workflow1.RuntimeConfig.RenderPassPerFrame
import com.squareup.workflow1.RuntimeConfig.RenderingPerFrame
import com.squareup.workflow1.WorkflowExperimentalRuntime

public class JvmTestRuntimeConfigTools {
  public companion object {
    /**
     * Helper for Configuration for the Workflow Runtime while running tests on the JVM.
     * This allows one to specify a project property from the gradle build to choose a runtime.
     * e.g. add "-Pworkflow.runtime=pass-per-frame" in your gradle build to build the
     * [RenderPassPerFrame] runtime
     *
     * The [WorkflowTestRuntime] already calls this utility, but if starting your own runtime, then
     * call this function and pass the result to the call to [renderWorkflowIn] as the
     * [RuntimeConfig].
     *
     * Current options are:
     * "pass-per-action" : [RenderPassPerAction] The original Workflow Runtime.
     *      Note that this doesn't need to be specified as it is the current default and is assumed
     *      by this utility.
     * "pass-per-frame" : [RenderPassPerFrame] see documentation there.
     * "rendering-per-frame" : [RenderingPerFrame] see documentation there.
     */
    @OptIn(WorkflowExperimentalRuntime::class)
    public fun getTestRuntimeConfig(): RuntimeConfig {
      val runtimeConfig = System.getProperty("workflow.runtime", "pass-per-action")
      return when (runtimeConfig) {
        "pass-per-frame" -> RenderPassPerFrame()
        "rendering-per-frame" -> RenderingPerFrame()
        else -> RenderPassPerAction
      }
    }
  }
}
