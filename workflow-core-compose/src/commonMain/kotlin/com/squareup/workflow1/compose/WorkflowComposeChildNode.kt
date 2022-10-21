package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowChildNode
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowNode

/**
 * @see [WorkflowChildNode]. This version supports piping Composable [Rendering].
 */
@WorkflowExperimentalRuntime
public class WorkflowComposeChildNode<
  ChildPropsT,
  ChildOutputT,
  ParentPropsT,
  ParentStateT,
  ParentOutputT
  >(
  workflow: Workflow<*, ChildOutputT, *>,
  handler: (ChildOutputT) -> WorkflowAction<ParentPropsT, ParentStateT, ParentOutputT>,
  workflowNode: WorkflowNode<ChildPropsT, *, ChildOutputT, *>
) : WorkflowChildNode<
  ChildPropsT,
  ChildOutputT,
  ParentPropsT,
  ParentStateT,
  ParentOutputT
  >(
  workflow, handler, workflowNode
) {

  @Composable
  public fun <R> Rendering(
    workflow: StatefulComposeWorkflow<*, *, *, *>,
    props: Any?
  ): R {
    val rendering = remember { mutableStateOf<R?>(null) }
    val node = remember {
      @Suppress("UNCHECKED_CAST")
      (workflowNode as WorkflowComposeNode<ChildPropsT, out Any?, ChildOutputT, R>)
    }
    val reifiedWorkflow = remember(workflow) {
      @Suppress("UNCHECKED_CAST")
      workflow as StatefulComposeWorkflow<ChildPropsT, out Any?, ChildOutputT, Nothing>
    }
    val reifiedProps = remember(props) {
      @Suppress("UNCHECKED_CAST")
      props as ChildPropsT
    }
    node.Rendering(
      reifiedWorkflow,
      reifiedProps,
      rendering,
    )
    @Suppress("UNCHECKED_CAST") // R can be nullable.
    return rendering.value as R
  }
}
