@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

/**
 * Optional interface that [Workflow]s should implement if they need the runtime to consider their
 * identity to include a child workflow's identity. Two [ImpostorWorkflow]s with the same concrete
 * class, but different [realIdentifier]s will be considered different workflows by the runtime.
 *
 * This is intended to be used for helper workflows that implement things like operators by wrapping
 * and delegating to other workflows, and which need to be able to express that the identity of the
 * operator workflow is derived from the identity of the wrapped workflow.
 */
@ExperimentalWorkflowApi
interface ImpostorWorkflow {
  /**
   * The [WorkflowIdentifier] of another workflow to be combined with the identifier of this
   * workflow, as obtained by [Workflow.identifier].
   *
   * For workflows that implement operators, this should be the identifier of the upstream
   * [Workflow] that this workflow wraps.
   */
  val realIdentifier: WorkflowIdentifier

  /**
   * Returns a string that describes how this workflow is related to [realIdentifier].
   */
  fun describeRealIdentifier(): String? = null
}
