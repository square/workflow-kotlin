package com.squareup.workflow1

/**
 * If your Workflow caches its [WorkflowIdentifier] (to avoid frequent lookups) then implement
 * this interface. Note that [StatefulWorkflow] and [StatelessWorkflow] already implement this,
 * so you only need to do so if you do not extend one of those classes.
 *
 * If your Workflow is an [ImpostorWorkflow] use the lazy delegate pattern that [StatefulWorkflow]
 * and [StatelessWorkflow] do in order to initialize everything in the proper order.
 */
public interface IdCacheable {

  public val cachedIdentifier: WorkflowIdentifier
}
