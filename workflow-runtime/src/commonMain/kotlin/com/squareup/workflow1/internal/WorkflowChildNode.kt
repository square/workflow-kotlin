package com.squareup.workflow1.internal

import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.internal.InlineLinkedList.InlineListNode
import com.squareup.workflow1.trace

/**
 * Representation of a child workflow that has been rendered by another workflow.
 *
 * Associates the child's [AbstractWorkflowNode] (which includes the key passed to `renderChild`) with the
 * output handler function that was passed to `renderChild`.
 */
internal class WorkflowChildNode<
  ChildPropsT,
  ChildOutputT,
  ParentPropsT,
  ParentStateT,
  ParentOutputT
  >(
  val workflow: Workflow<*, ChildOutputT, *>,
  private var handler: (ChildOutputT) -> WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT>,
  val workflowNode: AbstractWorkflowNode<ChildPropsT, ChildOutputT, *>
) : InlineListNode<WorkflowChildNode<*, *, *, *, *>> {
  override var nextListNode: WorkflowChildNode<*, *, *, *, *>? = null

  /** The [AbstractWorkflowNode]'s [WorkflowNodeId]. */
  val id get() = workflowNode.id

  /**
   * Returns true if this child has the same type as [otherWorkflow] and key as [key].
   */
  fun matches(
    otherWorkflow: Workflow<*, *, *>,
    key: String,
    workflowTracer: WorkflowTracer?
  ): Boolean = workflowTracer.trace("matches") { id.matches(otherWorkflow, key) }

  /**
   * Updates the handler function that will be invoked by [acceptChildOutput].
   */
  fun <CO, CP, S, O> setHandler(newHandler: (CO) -> WorkflowAction<CP, S, O>) {
    @Suppress("UNCHECKED_CAST")
    handler =
      newHandler as (ChildOutputT) -> WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT>
  }

  /**
   * Wrapper around [AbstractWorkflowNode.render] that allows calling it with erased types.
   */
  fun <R> render(
    workflow: Workflow<*, *, *>,
    props: Any?
  ): R {
    @Suppress("UNCHECKED_CAST")
    return workflowNode.render(
      workflow as Workflow<ChildPropsT, ChildOutputT, Nothing>,
      props as ChildPropsT
    ) as R
  }

  /**
   * Wrapper around [handler] that allows calling it with erased types.
   */
  @Suppress("UNCHECKED_CAST")
  fun acceptChildOutput(output: Any?): WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT> =
    handler(output as ChildOutputT)
}
