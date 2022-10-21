package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.squareup.workflow1.RealRenderContext
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel

/**
 * @see [RealRenderContext]. This is the version that supports Compose runtime optimizations.
 */
public class RealComposeRenderContext<PropsT, StateT, OutputT>(
  override val renderer: ComposeRenderer<PropsT, StateT, OutputT>,
  sideEffectRunner: SideEffectRunner,
  eventActionsChannel: SendChannel<WorkflowAction<PropsT, StateT, OutputT>>
) : RealRenderContext<PropsT, StateT, OutputT>(
  renderer,
  sideEffectRunner,
  eventActionsChannel,
),
  BaseComposeRenderContext<PropsT, StateT, OutputT> {

  public interface ComposeRenderer<PropsT, StateT, OutputT> : Renderer<PropsT, StateT, OutputT> {
    @Composable
    public fun <ChildPropsT, ChildOutputT, ChildRenderingT> Rendering(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
    ): ChildRenderingT
  }

  @Composable
  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> ChildRendering(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT {
    remember(this) {
      checkNotFrozen()
    }
    return renderer.Rendering(child, props, key, handler)
  }

  @Composable
  override fun RunningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    remember(this) {
      checkNotFrozen()
    }
    sideEffectRunner.runningSideEffect(key, sideEffect)
  }
}
