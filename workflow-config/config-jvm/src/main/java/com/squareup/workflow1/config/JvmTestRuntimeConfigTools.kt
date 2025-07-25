package com.squareup.workflow1.config

import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.DRAIN_EXCLUSIVE_ACTIONS
import com.squareup.workflow1.RuntimeConfigOptions.PARTIAL_TREE_RENDERING
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
import com.squareup.workflow1.RuntimeConfigOptions.STABLE_EVENT_HANDLERS
import com.squareup.workflow1.RuntimeConfigOptions.WORK_STEALING_DISPATCHER
import com.squareup.workflow1.WorkflowExperimentalRuntime

public class JvmTestRuntimeConfigTools {
  public companion object {
    /**
     * Helper for Configuration for the workflow runtime in an application.
     * This allows one to specify a project property from the gradle build to choose a runtime.
     * e.g. add `-Pworkflow.runtime=conflate` in your gradle build to build the conflate runtime
     * into the application.
     *
     * The [WorkflowTestRuntime][com.squareup.workflow1.testing.WorkflowTestRuntime]
     * and [RenderTester][com.squareup.workflow1.testing.RenderTester] runtimes
     * already call this utility. To honor this property from your own runtime call this
     * function and pass the result to the call to
     * [renderWorkflowIn][com.squareup.workflow1.renderWorkflowIn] as the [RuntimeConfig] parameter.
     *
     * Current options (can be combined with `-` characters, e.g. `conflate-partial`):
     *
     * - `conflate` Process all queued actions before passing rendering to the UI layer.
     *
     * - `stateChange` Only re-render when the state of some WorkflowNode has been changed by an
     *   action cascade.
     *
     * - `partial` Partial tree rendering, which only re-renders each Workflow node if: 1) its
     *    state changed; or 2) one of its descendant's state changed. (This option requires
     *    `stateChange`, and enables it as well.)
     *
     * - `stable` Enables stable event handlers (changes the default value of the `remember`
     *    parameter of `RenderContext.eventHandler` functions from `false` to `true`)
     *
     * - `drainExclusive` Enables draining exclusive actions. If we have more actions to process
     *    that are queued on nodes not affected by the last action application, then we will
     *    continue to process those actions before another render pass.
     *
     * - `stealingDispatcher` Enables turning on the [WorkStealingDispatcher] to try and drain
     *    available tasks.
     *
     * - `all` Enables all optimizations.
     */
    @OptIn(WorkflowExperimentalRuntime::class)
    public fun getTestRuntimeConfig(): RuntimeConfig {
      val selection = System.getProperty("workflow.runtime", "").split("-")
        // We used to have a no-op `baseline` option, let's not choke on it.
        .filterNot { it == "baseline" || it.isBlank() }
        .toSet()

      val config = mutableSetOf<RuntimeConfigOptions>()
      selection.forEach {
        when (it) {
          "conflate" -> config.add(CONFLATE_STALE_RENDERINGS)
          "stateChange" -> config.add(RENDER_ONLY_WHEN_STATE_CHANGES)
          "partial" -> config.addAll(setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING))
          "stable" -> config.add(STABLE_EVENT_HANDLERS)
          "drainExclusive" -> config.add(DRAIN_EXCLUSIVE_ACTIONS)
          "stealingDispatcher" -> config.add(WORK_STEALING_DISPATCHER)
          "all" -> config.addAll(RuntimeConfigOptions.ALL)
          else -> throw IllegalArgumentException("Unrecognized runtime config option \"$it\"")
        }
      }

      return config
    }
  }
}
