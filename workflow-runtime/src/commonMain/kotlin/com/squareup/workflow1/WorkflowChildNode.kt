package com.squareup.workflow1

import com.squareup.workflow1.InlineLinkedList.InlineListNode

/**
 * Representation of a child workflow that has been rendered by another workflow.
 *
 * Associates the child's [WorkflowNode] (which includes the key passed to `renderChild`) with the
 * output handler function that was passed to `renderChild`.
 */
public open class WorkflowChildNode<
  ChildPropsT,
  ChildOutputT,
  ParentPropsT,
  ParentStateT,
  ParentOutputT
  >(
  public val workflow: Workflow<*, ChildOutputT, *>,
  private var handler: (ChildOutputT) -> WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT>,
  public val workflowNode: WorkflowNode<ChildPropsT, *, ChildOutputT, *>
) : InlineListNode<WorkflowChildNode<*, *, *, *, *>> {
  override var nextListNode: WorkflowChildNode<*, *, *, *, *>? = null

  /** The [WorkflowNode]'s [WorkflowNodeId]. */
  public val id: WorkflowNodeId get() = workflowNode.id

  /**
   * Returns true if this child has the same type as [otherWorkflow] and key as [key].
   */
  public fun matches(
    otherWorkflow: Workflow<*, *, *>,
    key: String
  ): Boolean = id.matches(otherWorkflow, key)

  /**
   * Updates the handler function that will be invoked by [acceptChildOutput].
   */
  internal fun <CO, CP, S, O> setHandler(newHandler: (CO) -> WorkflowAction<CP, S, O>) {
    @Suppress("UNCHECKED_CAST")
    handler =
      newHandler as (ChildOutputT) -> WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT>
  }

  /**
   * Wrapper around [WorkflowNode.render] that allows calling it with erased types.
   */
  public fun <R> render(
    workflow: StatefulWorkflow<*, *, *, *>,
    props: Any?
  ): R {
    @Suppress("UNCHECKED_CAST")
    return workflowNode.render(
      workflow as StatefulWorkflow<ChildPropsT, out Any?, ChildOutputT, Nothing>,
      props as ChildPropsT
    ) as R
  }

  /**
   * Wrapper around [handler] that allows calling it with erased types.
   */
  @Suppress("UNCHECKED_CAST")
  public fun acceptChildOutput(output: Any?):
    WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT> =
    handler(output as ChildOutputT)
}
