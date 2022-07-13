package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowAction.Companion.noAction

/**
 * @see [BaseRenderContext]. This is the version which adds support for the Compose optimized
 * runtime.
 */
public interface BaseComposeRenderContext<out PropsT, StateT, in OutputT> :
  BaseRenderContext<PropsT, StateT, OutputT> {

  /**
   * @see [BaseRenderContext.renderChild] as this is equivalent, except as a Composable.
   */
  @Composable
  public fun <ChildPropsT, ChildOutputT, ChildRenderingT> ChildRendering(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT
}

/**
 * Convenience alias of [BaseComposeRenderContext.ChildRendering] for workflows that don't take props.
 */
@Composable
public fun <PropsT, StateT, OutputT, ChildOutputT, ChildRenderingT>
BaseComposeRenderContext<PropsT, StateT, OutputT>.ChildRendering(
  child: Workflow<Unit, ChildOutputT, ChildRenderingT>,
  key: String = "",
  handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
): ChildRenderingT = ChildRendering(child, Unit, key, handler)
/**
 * Convenience alias of [BaseComposeRenderContext.ChildRendering] for workflows that don't emit output.
 */
@Composable
public fun <PropsT, ChildPropsT, StateT, OutputT, ChildRenderingT>
BaseComposeRenderContext<PropsT, StateT, OutputT>.ChildRendering(
  child: Workflow<ChildPropsT, Nothing, ChildRenderingT>,
  props: ChildPropsT,
  key: String = "",
): ChildRenderingT = ChildRendering(child, props, key) { noAction() }
/**
 * Convenience alias of [BaseComposeRenderContext.ChildRendering] for children that don't take props or emit
 * output.
 */
@Composable
public fun <PropsT, StateT, OutputT, ChildRenderingT>
BaseComposeRenderContext<PropsT, StateT, OutputT>.ChildRendering(
  child: Workflow<Unit, Nothing, ChildRenderingT>,
  key: String = "",
): ChildRenderingT = ChildRendering(child, Unit, key) { noAction() }
