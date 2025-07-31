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
  val shortCircuitConfig = config.contains(RENDER_ONLY_WHEN_STATE_CHANGES)
  val csrConfig = config.contains(CONFLATE_STALE_RENDERINGS)
  val ptrConfig = config.contains(PARTIAL_TREE_RENDERING)
  val deaConfig = config.contains(DRAIN_EXCLUSIVE_ACTIONS)
  val sehConfig = config.contains(STABLE_EVENT_HANDLERS)
  val wsdConfig = config.contains(WORK_STEALING_DISPATCHER)

  val configAsString = config.toString()
}
