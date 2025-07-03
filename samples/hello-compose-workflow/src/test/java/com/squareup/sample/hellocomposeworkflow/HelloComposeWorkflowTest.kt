package com.squareup.sample.hellocomposeworkflow

import androidx.compose.runtime.Composable
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.renderChild
import com.squareup.workflow1.identifier
import com.squareup.workflow1.testing.expectWorkflow
import com.squareup.workflow1.testing.testRender
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(WorkflowExperimentalApi::class)
class HelloComposeWorkflowTest {

  @Test fun foo() {
    val child1 = Workflow.compose<Unit, Nothing, String> { _, _ -> "child1" }
    val child2 = Workflow.compose<Unit, Nothing, String> { _, _ -> "child2" }
    val workflow = Workflow.compose<Unit, Nothing, String> { _, _ ->
      renderChild(child1) + renderChild(child2)
    }

    workflow.testRender(Unit)
      .expectWorkflow(child1.identifier, "fakechild1")
      .expectWorkflow(child2.identifier, "fakechild2")
      .render { rendering ->
        assertEquals("fakechild1fakechild2", rendering)
      }
  }
}

/**
 * Returns a stateless [Workflow] via the given [render] function.
 *
 * Note that while the returned workflow doesn't have any _internal_ state of its own, it may use
 * [props][PropsT] received from its parent, and it may render child workflows that do have
 * their own internal state.
 */
@WorkflowExperimentalApi
inline fun <PropsT, OutputT, RenderingT> Workflow.Companion.compose(
  crossinline produceRendering: @Composable (
    props: PropsT,
    emitOutput: (OutputT) -> Unit
  ) -> RenderingT
): Workflow<PropsT, OutputT, RenderingT> =
  object : ComposeWorkflow<PropsT, OutputT, RenderingT>() {
    @Composable override fun produceRendering(
      props: PropsT,
      emitOutput: (OutputT) -> Unit
    ): RenderingT = produceRendering(props, emitOutput)
  }
