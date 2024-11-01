package com.squareup.workflow1.testing

import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.stateless
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderIdempotencyCheckerTest {

  @Test fun `renders tree twice`() {
    var leafRenders = 0
    var rootRenders = 0
    // Use a tree depth of at least two to test that we're not double-rendering every _workflow_.
    val leafWorkflow = Workflow.stateless<Unit, Nothing, Unit> { leafRenders++ }
    val rootWorkflow = Workflow.stateless<Unit, Nothing, Unit> {
      rootRenders++
      renderChild(leafWorkflow)
    }
    val scope = TestScope()

    renderWorkflowIn(
      rootWorkflow,
      scope,
      MutableStateFlow(Unit),
      interceptors = listOf(RenderIdempotencyChecker)
    ) {}
    assertEquals(2, rootRenders)
    assertEquals(2, leafRenders)
  }

  @Test fun `events sent to sink read after render are accepted`() {
    val workflow = Workflow.stateless<Unit, String, (String) -> Unit> {
      {
          value: String ->
        actionSink.send(
          action("") {
            setOutput(value)
          }
        )
      }
    }
    val outputs = mutableListOf<String>()
    val renderings = renderWorkflowIn(
      workflow,
      CoroutineScope(Unconfined),
      MutableStateFlow(Unit),
      interceptors = listOf(RenderIdempotencyChecker)
    ) {
      outputs += it
    }

    assertEquals(emptyList(), outputs)
    renderings.value.rendering.invoke("poke")
    assertEquals(listOf("poke"), outputs)
  }

  @Test fun `events sent to sink captured during render are accepted`() {
    val workflow = Workflow.stateless<Unit, String, (String) -> Unit> {
      val sink = actionSink
      { value: String ->
        sink.send(
          action("") {
            setOutput(value)
          }
        )
      }
    }
    val outputs = mutableListOf<String>()
    val renderings = renderWorkflowIn(
      workflow,
      CoroutineScope(Unconfined),
      MutableStateFlow(Unit),
      interceptors = listOf(RenderIdempotencyChecker)
    ) {
      outputs += it
    }

    assertEquals(emptyList(), outputs)
    renderings.value.rendering.invoke("poke")
    assertEquals(listOf("poke"), outputs)
  }
}
