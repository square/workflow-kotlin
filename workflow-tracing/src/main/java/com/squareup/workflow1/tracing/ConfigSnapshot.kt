package com.squareup.workflow1.tracing

import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions.CONFLATE_STALE_RENDERINGS
import com.squareup.workflow1.RuntimeConfigOptions.DRAIN_EXCLUSIVE_ACTIONS
import com.squareup.workflow1.RuntimeConfigOptions.PARTIAL_TREE_RENDERING
import com.squareup.workflow1.RuntimeConfigOptions.RENDER_ONLY_WHEN_STATE_CHANGES
import com.squareup.workflow1.RuntimeConfigOptions.STABLE_EVENT_HANDLERS
import com.squareup.workflow1.RuntimeConfigOptions.WORK_STEALING_DISPATCHER
import com.squareup.workflow1.WorkflowExperimentalRuntime

/**
 * Snapshot of the current [RuntimeConfig]
 */
@OptIn(WorkflowExperimentalRuntime::class)
public class ConfigSnapshot(config: RuntimeConfig) {
  public val configAsString: String = config.toString()

  public val shortConfigAsString: String by lazy {
    buildString {
      append("Config:")
      if (config.contains(RENDER_ONLY_WHEN_STATE_CHANGES)) {
        append("ROWSC, ")
      }
      if (config.contains(CONFLATE_STALE_RENDERINGS)) {
        append("CSR, ")
      }
      if (config.contains(PARTIAL_TREE_RENDERING)) {
        append("PTR, ")
      }
      if (config.contains(DRAIN_EXCLUSIVE_ACTIONS)) {
        append("DEA, ")
      }
      if (config.contains(STABLE_EVENT_HANDLERS)) {
        append("SEH, ")
      }
      if (config.contains(WORK_STEALING_DISPATCHER)) {
        append("WSD, ")
      }
      if (config.isEmpty()) {
        append("Base, ")
      }
    }
  }
}
