package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.squareup.workflow1.ImpostorWorkflow
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.action
import com.squareup.workflow1.identifier

/**
 * Uses the given [function][transform] to transform a [Workflow] that
 * renders [FromRenderingT] to one renders [ToRenderingT],
 */
@OptIn(WorkflowExperimentalRuntime::class)
public fun <PropsT, OutputT, FromRenderingT, ToRenderingT>
Workflow<PropsT, OutputT, FromRenderingT>.mapRenderingComposable(
  transform: (FromRenderingT) -> ToRenderingT
): Workflow<PropsT, OutputT, ToRenderingT> =
  object : StatelessComposeWorkflow<PropsT, OutputT, ToRenderingT>(), ImpostorWorkflow {
    override val realIdentifier: WorkflowIdentifier get() = this@mapRenderingComposable.identifier

    override fun render(
      renderProps: PropsT,
      context: StatelessWorkflow<PropsT, OutputT, ToRenderingT>.RenderContext
    ): ToRenderingT {
      val rendering = context.renderChild(this@mapRenderingComposable, renderProps) { output ->
        action({ "mapRendering" }) { setOutput(output) }
      }
      return transform(rendering)
    }

    @Composable
    override fun Rendering(
      renderProps: PropsT,
      context: RenderContext
    ): ToRenderingT {
      val rendering =
        context.ChildRendering(this@mapRenderingComposable, renderProps, "") { output ->
          action({ "mapRendering" }) { setOutput(output) }
        }
      val transformed = remember(rendering) {
        transform(rendering)
      }
      return transformed
    }

    override fun describeRealIdentifier(): String =
      "${this@mapRenderingComposable.identifier}.mapRendering()"

    override fun toString(): String = "${this@mapRenderingComposable}.mapRendering()"
  }
