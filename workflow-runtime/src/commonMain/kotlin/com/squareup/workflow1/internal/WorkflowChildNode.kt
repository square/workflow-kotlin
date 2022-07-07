package com.squareup.workflow1.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.internal.InlineLinkedList.InlineListNode

/**
 * Representation of a child workflow that has been rendered by another workflow.
 *
 * Associates the child's [WorkflowNode] (which includes the key passed to `renderChild`) with the
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
  val workflowNode: WorkflowNode<ChildPropsT, *, ChildOutputT, *>
) : InlineListNode<WorkflowChildNode<*, *, *, *, *>> {
  override var nextListNode: WorkflowChildNode<*, *, *, *, *>? = null

  /** The [WorkflowNode]'s [WorkflowNodeId]. */
  val id get() = workflowNode.id

  /**
   * Returns true if this child has the same type as [otherWorkflow] and key as [key].
   */
  fun matches(
    otherWorkflow: Workflow<*, *, *>,
    key: String
  ): Boolean = id.matches(otherWorkflow, key)

  /**
   * Updates the handler function that will be invoked by [acceptChildOutput].
   */
  fun <CO, CP, S, O> setHandler(newHandler: (CO) -> WorkflowAction<CP, S, O>) {
    @Suppress("UNCHECKED_CAST")
    handler =
      newHandler as (ChildOutputT) -> WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT>
  }

  /**
   * Wrapper around [WorkflowNode.render] that allows calling it with erased types.
   */
  fun <R> render(
    workflow: StatefulWorkflow<*, *, *, *>,
    props: Any?
  ): R {
    @Suppress("UNCHECKED_CAST")
    return workflowNode.render(
      workflow as StatefulWorkflow<ChildPropsT, out Any?, ChildOutputT, Nothing>,
      props as ChildPropsT
    ) as R
  }

  @Composable
  fun <R> Rendering(
    workflow: StatefulWorkflow<*, *, *, *>,
    props: Any?
  ): R {
    val rendering = remember { mutableStateOf<R?>(null) }
    @Suppress("UNCHECKED_CAST")
    (workflowNode as WorkflowNode<ChildPropsT, out Any?, ChildOutputT, R>).Rendering(
      workflow as StatefulWorkflow<ChildPropsT, out Any?, ChildOutputT, Nothing>,
      props as ChildPropsT,
      rendering,
    )
    return rendering.value!!
  }

  /**
   * Wrapper around [handler] that allows calling it with erased types.
   */
  @Suppress("UNCHECKED_CAST")
  fun acceptChildOutput(output: Any?): WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT> =
    handler(output as ChildOutputT)
}
