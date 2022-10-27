package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.squareup.workflow1.IdCounter
import com.squareup.workflow1.SubtreeManager
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowNodeId
import com.squareup.workflow1.id
import com.squareup.workflow1.identifier
import kotlin.coroutines.CoroutineContext

/**
 * @see [SubtreeManager]. This is the version which adds support for the Compose optimized runtime.
 */
@WorkflowExperimentalRuntime
public class ComposeSubtreeManager<PropsT, StateT, OutputT>(
  snapshotCache: Map<WorkflowNodeId, TreeSnapshot>?,
  contextForChildren: CoroutineContext,
  emitActionToParent: (WorkflowAction<PropsT, StateT, OutputT>) -> Any?,
  workflowSession: WorkflowSession? = null,
  override val interceptor: ComposeWorkflowInterceptor = NoopComposeWorkflowInterceptor,
  idCounter: IdCounter? = null
) : SubtreeManager<PropsT, StateT, OutputT>(
  snapshotCache,
  contextForChildren,
  emitActionToParent,
  workflowSession,
  interceptor,
  idCounter
),
  RealComposeRenderContext.ComposeRenderer<PropsT, StateT, OutputT> {

  @Composable
  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> Rendering(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT {
    val stagedChild =
      StagedChild(
        child,
        props,
        key,
        handler
      )
    val statefulChild = remember(child) { child.asStatefulWorkflow().asComposeWorkflow() }
    return stagedChild.Rendering(statefulChild, props)
  }

  /**
   * Prepare the staged child while only modifying [children] in a SideEffect. This will ensure
   * that we do not inappropriately modify non-snapshot state.
   */
  @Composable
  private fun <ChildPropsT, ChildOutputT, ChildRenderingT> StagedChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): WorkflowComposeChildNode<*, *, *, *, *> {
    val childState = remember(child, key, props, handler) {
      children.forEachStaging {
        require(!(it.matches(child, key))) {
          "Expected keys to be unique for ${child.identifier}: key=\"$key\""
        }
      }
      mutableStateOf(
        children.firstActiveOrNull {
          it.matches(child, key)
        } ?: createChildNode(child, props, key, handler)
      )
    }

    SideEffect {
      // Modify the [children] lists in a side-effect when composition is committed.
      children.removeAndStage(
        predicate = { it.matches(child, key) },
        child = childState.value
      )
    }
    return childState.value as WorkflowComposeChildNode<*, *, *, *, *>
  }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> createChildNode(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    initialProps: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): WorkflowComposeChildNode<ChildPropsT, ChildOutputT, PropsT, StateT, OutputT> {
    val id = child.id(key)
    lateinit var node: WorkflowComposeChildNode<ChildPropsT, ChildOutputT, PropsT, StateT, OutputT>

    fun acceptChildOutput(output: ChildOutputT): Any? {
      val action = node.acceptChildOutput(output)
      return emitActionToParent(action)
    }

    val childTreeSnapshots = snapshotCache?.get(id)

    val workflowNode = WorkflowComposeNode(
      id = id,
      child.asStatefulWorkflow().asComposeWorkflow(),
      initialProps,
      childTreeSnapshots,
      contextForChildren,
      ::acceptChildOutput,
      workflowSession,
      interceptor,
      idCounter = idCounter
    ).apply {
      startSession()
    }
    return WorkflowComposeChildNode(child, handler, workflowNode)
      .also { node = it }
  }
}
