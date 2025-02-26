package com.squareup.workflow1

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/**
 * A [Workflow]-like interface that participates in a workflow tree via its [Rendering] composable.
 */
@Stable
public interface ComposeWorkflow<
  in PropsT,
  out OutputT,
  out RenderingT
  > {

  /**
   * The main composable of this workflow that consumes some [props] from its parent and may emit
   * an output via [emitOutput].
   *
   * Equivalent to [StatefulWorkflow.render].
   */
  @WorkflowComposable
  @Composable
  fun Rendering(
    props: PropsT,
    emitOutput: (OutputT) -> Unit
  ): RenderingT
}

fun <
  PropsT, StateT, OutputT,
  ChildPropsT, ChildOutputT, ChildRenderingT
  > BaseRenderContext<PropsT, StateT, OutputT>.renderChild(
  child: ComposeWorkflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
  props: ChildPropsT,
  key: String = "",
  handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
): ChildRenderingT = renderComposable(key = key) {
  // Explicitly remember the output function since we know that actionSink is stable even though
  // Compose might not know that.
  val emitOutput: (ChildOutputT) -> Unit = remember(actionSink) {
    { output ->
      val action = handler(output)
      actionSink.send(action)
    }
  }
  child.Rendering(
    props = props,
    emitOutput = emitOutput
  )
}
