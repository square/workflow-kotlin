package com.squareup.workflow1.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfig.Companion
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.identifier
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

/**
 * Responsible for tracking child workflows, starting them and tearing them down when necessary.
 * Also manages restoring children from snapshots.
 *
 * Child workflows are stored in [WorkflowChildNode]s, which associate the child's [WorkflowNode]
 * with its output handler.
 *
 * ## Rendering
 *
 * This class implements [RealRenderContext.Renderer], and [WorkflowNode] will pass its instance
 * of this class to the [RealRenderContext] on each render pass to render children. That means that
 * when a workflow renders a child, this class does the actual work.
 *
 * This class keeps two lists:
 *  1. Active list: All the children from the last render pass that have not yet been rendered in
 *     the subsequent pass.
 *  2. Staging list: Children that have been rendered in the current render pass, before the pass is
 *     [committed][commitRenderedChildren].
 *
 * The render process is as follows:
 *   1. When the render pass starts, the staging list is empty and the active list contains all the
 *      children rendered in the last pass.
 *      ```
 *      active:  [foo, bar]
 *      staging: []
 *      ```
 *   2. Every time a child is rendered, it is looked up in the list of children from the last render
 *      pass. If found, it is removed from the active list and added to the staging list.
 *      ```
 *      render(foo)
 *      active:  [bar]
 *      staging: [foo]
 *      ```
 *   3. If not found, a new [WorkflowChildNode] is created and added to the staging list.
 *      ```
 *      render(baz)
 *      active:  [bar]
 *      staging: [foo, baz]
 *      ```
 *   4. When the workflow's render method returns, the [WorkflowNode] calls
 *      [commitRenderedChildren], which:
 *        1. Tears down all the children remaining in the active list
 *           ```
 *           bar.cancel()
 *           active:  [bar]
 *           staging: [foo, baz]
 *           ```
 *        2. Clears the old active list
 *           ```
 *           active:  []
 *           staging: [foo, baz]
 *           ```
 *        3. And then swaps the active and staging lists.
 *           ```
 *           active:  [foo, baz]
 *           staging: []
 *           ```
 *      This just updates a couple references, and since the lists are swapped, doesn't involve any
 *      allocations.
 *
 * When looking up a child in the active list, a linear search is used. This is expected to perform
 * adequately in practice because most workflows don't have a large number of children (even as few
 * as ten is uncommon), and in the most common case, the structure of the workflow tree doesn't
 * change (no workflows are added or removed), and children are re-rendered in the same order as
 * before, so the first active child will usually match.
 *
 * @param snapshotCache When this manager's node is restored from a snapshot, its children
 * snapshots are extracted into this cache. Then, when those children are started for the
 * first time, they are also restored from their snapshots.
 */
internal class SubtreeManager<PropsT, StateT, OutputT>(
  private var snapshotCache: Map<WorkflowNodeId, TreeSnapshot>?,
  private val contextForChildren: CoroutineContext,
  private val emitActionToParent: (WorkflowAction<PropsT, StateT, OutputT>) -> Any?,
  private val runtimeConfig: RuntimeConfig = Companion.DEFAULT_CONFIG,
  private val workflowSession: WorkflowSession? = null,
  private val interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  private val idCounter: IdCounter? = null
) : RealRenderContext.Renderer<PropsT, StateT, OutputT> {
  private var children = ActiveStagingList<WorkflowChildNode<*, *, *, *, *>>()
  var currentComposer: Composer? = null

  /**
   * Moves all the nodes that have been accumulated in the staging list to the active list, making
   * them the new active list, and tears down any inactive children.
   *
   * This should be called after this node's render method returns.
   */
  fun commitRenderedChildren() {
    // Any children left in the previous active list after the render finishes were not re-rendered
    // and must be torn down.
    children.commitStaging { child ->
      child.workflowNode.cancel()
    }
    // Get rid of any snapshots that weren't applied on the first render pass.
    // They belong to children that were saved but not restarted.
    snapshotCache = null
  }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> render(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT {
    if (runtimeConfig.useComposeInRuntime) {
      // Composed version of render
      val Rendering: @Composable (
        Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
        ChildPropsT,
        String,
        (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
      ) -> ChildRenderingT = @Composable
      { _child, _props, _key, _handler ->
        val stagedChild =
          StagedChild(
            _child,
            _props,
            _key,
            _handler
          )
        val statefulChild = remember(child) { _child.asStatefulWorkflow() }
        stagedChild.Rendering(statefulChild, props)
      }

      currentComposer!!.startReplaceableGroup(0x48ae8da7)

      // Cast this so that we can call into the Compose runtime directly.
      @Suppress("UNCHECKED_CAST")
      val composerRendering = Rendering as (
        Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
        ChildPropsT,
        String,
        (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>,
        Composer,
        Int
      ) -> ChildRenderingT

      val rendering = composerRendering(child, props, key, handler, currentComposer!!, 0)
      currentComposer!!.endReplaceableGroup()
      return rendering
    }
    val stagedChild = prepareStagedChild(
      child,
      props,
      key,
      handler
    )
    return stagedChild.render(child.asStatefulWorkflow(), props)
  }

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
    val statefulChild = remember(child) { child.asStatefulWorkflow() }
    return stagedChild.Rendering(statefulChild, props)
  }

  private fun <ChildPropsT, ChildOutputT, ChildRenderingT> prepareStagedChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): WorkflowChildNode<*, *, *, *, *> {
    // Prevent duplicate workflows with the same key.
    children.forEachStaging {
      require(!(it.matches(child, key))) {
        "Expected keys to be unique for ${child.identifier}: key=\"$key\""
      }
    }

    // Start tracking this case so we can be ready to render it.
    val stagedChild = children.retainOrCreate(
      predicate = { it.matches(child, key) },
      create = { createChildNode(child, props, key, handler) }
    )
    stagedChild.setHandler(handler)
    return stagedChild
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
  ): WorkflowChildNode<*, *, *, *, *> {
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
    return childState.value
  }

  /**
   * Uses [selector] to invoke [WorkflowNode.tick] for every running child workflow this instance
   * is managing.
   */
  fun tickChildren(selector: SelectBuilder<ActionProcessingResult?>) {
    children.forEachActive { child ->
      child.workflowNode.tick(selector)
    }
  }

  fun createChildSnapshots(): Map<WorkflowNodeId, TreeSnapshot> {
    val snapshots = mutableMapOf<WorkflowNodeId, TreeSnapshot>()
    children.forEachActive { child ->
      val childWorkflow = child.workflow.asStatefulWorkflow()
      snapshots[child.id] = child.workflowNode.snapshot(childWorkflow)
    }
    return snapshots
  }

  private fun <ChildPropsT, ChildOutputT, ChildRenderingT> createChildNode(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    initialProps: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): WorkflowChildNode<ChildPropsT, ChildOutputT, PropsT, StateT, OutputT> {
    val id = child.id(key)
    lateinit var node: WorkflowChildNode<ChildPropsT, ChildOutputT, PropsT, StateT, OutputT>

    fun acceptChildOutput(output: ChildOutputT): Any? {
      val action = node.acceptChildOutput(output)
      return emitActionToParent(action)
    }

    val childTreeSnapshots = snapshotCache?.get(id)

    val workflowNode = WorkflowNode(
      id = id,
      workflow = child.asStatefulWorkflow(),
      initialProps = initialProps,
      snapshot = childTreeSnapshots,
      baseContext = contextForChildren,
      runtimeConfig = runtimeConfig,
      emitOutputToParent = ::acceptChildOutput,
      parent = workflowSession,
      interceptor = interceptor,
      idCounter = idCounter
    )
    return WorkflowChildNode(child, handler, workflowNode)
      .also { node = it }
  }
}
