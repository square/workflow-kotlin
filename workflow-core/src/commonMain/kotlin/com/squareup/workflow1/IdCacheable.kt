package com.squareup.workflow1

import com.squareup.workflow1.compose.ComposeWorkflow

/**
 * If your Workflow caches its [WorkflowIdentifier] (to avoid frequent lookups) then implement
 * this interface. Note that built-in workflow types ([StatefulWorkflow], [StatelessWorkflow],
 * [ComposeWorkflow] etc.) already implement this, so you only need to do so if you do not extend
 * one of those classes.
 *
 * Your Workflow can just assign null to this value as the [identifier] extension will use it
 * for caching.
 */
public interface IdCacheable {

  public var cachedIdentifier: WorkflowIdentifier?
}
