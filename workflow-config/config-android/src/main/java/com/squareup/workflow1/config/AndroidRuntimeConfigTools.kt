package com.squareup.workflow1.config

import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.PARTIAL_TREE_RENDERING
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
import com.squareup.workflow1.WorkflowExperimentalRuntime

public class AndroidRuntimeConfigTools {

  public companion object {
    /**
     * Helper for Configuration for the workflow runtime in an application.
     * This allows one to specify a project property from the gradle build to choose a runtime.
     * e.g. add "-Pworkflow.runtime=conflate" in your gradle build to build the conflate runtime
     * into the application.
     *
     * Note that this must be specified in the application built for any ui/integration tests. Call
     * this function, and then pass that to the call to [renderWorkflowIn] as the [RuntimeConfig].
     *
     * Current options are:
     * "conflate" : Process all queued actions before passing rendering
     *      to the UI layer.
     * "baseline" : Original Workflow Runtime. Note that this doesn't need to
     *      be specified as it is the current default and is assumed by this utility.
     *
     * Then, these can be combined (via '-') with:
     * "stateChange" : Only re-render when the state of some WorkflowNode has been changed by an
     *   action cascade.
     * "partial" : Which includes "stateChange" as well as partial tree rendering, which only
     *   re-renders each Workflow node if: 1) its state changed; or 2) one of its descendant's state
     *   changed.
     *
     * E.g., "baseline-stateChange" to turn on the stateChange option with the baseline runtime.
     *
     */
    @WorkflowExperimentalRuntime
    public fun getAppWorkflowRuntimeConfig(): RuntimeConfig {
      return when (BuildConfig.WORKFLOW_RUNTIME) {
        "conflate" -> setOf(CONFLATE_STALE_RENDERINGS)
        "conflate-stateChange" -> setOf(CONFLATE_STALE_RENDERINGS, RENDER_ONLY_WHEN_STATE_CHANGES)
        "baseline-stateChange" -> setOf(RENDER_ONLY_WHEN_STATE_CHANGES)
        "conflate-partial" -> setOf(
          CONFLATE_STALE_RENDERINGS,
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING
        )
        "baseline-partial" -> setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING)
        "", "baseline" -> RuntimeConfigOptions.RENDER_PER_ACTION
        else ->
          throw IllegalArgumentException("Unrecognized config \"${BuildConfig.WORKFLOW_RUNTIME}\"")
      }
    }
  }
}
