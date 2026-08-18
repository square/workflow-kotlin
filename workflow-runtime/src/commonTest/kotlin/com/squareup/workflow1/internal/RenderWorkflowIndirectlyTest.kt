@file:OptIn(WorkflowExperimentalApi::class, WorkflowExperimentalRuntime::class)

package com.squareup.workflow1.internal

import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.RenderingHandle
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.action
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.internal.RenderWorkflowIndirectlyTest.TestWorkflow.Rendering
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.renderWorkflowIndirectly
import com.squareup.workflow1.stateless
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.fail

/**
 * Tests for [com.squareup.workflow1.BaseRenderContext.renderWorkflowIndirectly] and the traditional
 * runtime's [RenderingHandle] implementation.
 */
@ExperimentalCoroutinesApi
internal class RenderWorkflowIndirectlyTest {

  private class TestWorkflow : StatefulWorkflow<String, String, String, Rendering>() {

    var started = 0

    data class Rendering(
      val props: String,
      val eventHandler: (String) -> Unit
    )

    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ): String {
      started++
      return "initialState:$props"
    }

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext<String, String, String>
    ): Rendering = Rendering(
      props = renderProps,
      eventHandler = context.eventHandler("") { out -> setOutput("workflow output:$out") }
    )

    override fun snapshotState(state: String): Snapshot? = null
  }

  @Test fun renderIndirectly_returns_same_handle_across_render_passes() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    fun render() = manager.renderIndirectly(workflow, "props", key = "", handler = { fail() })
      .also { manager.commitRenderedChildren() }

    val first = render()
    val second = render()
    val third = render()

    assertSame(first, second)
    assertSame(first, third)
    // The child was only started once, i.e. these were all the same session.
    assertEquals(1, workflow.started)
  }

  @Test fun renderIndirectly_updates_current_rendering() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    fun render(props: String) =
      manager.renderIndirectly(workflow, props, key = "", handler = { fail() })
        .also { manager.commitRenderedChildren() }

    val handle = render("first")
    assertEquals("first", (handle.currentRendering as Rendering).props)

    render("second")
    assertEquals("second", (handle.currentRendering as Rendering).props)
  }

  @Test fun renderIndirectly_publishes_equal_but_distinct_renderings() {
    val manager = subtreeManagerForTest<String, String, String>()
    // All of this child's renderings are `equals`, but each one is a distinct instance – just like
    // a rendering whose only interesting content is the event handlers it closes over.
    val child = Workflow.stateless<String, Nothing, List<String>> { mutableListOf() }
    fun render() = manager.renderIndirectly(child, "props", key = "", handler = { fail() })
      .also { manager.commitRenderedChildren() }

    val handle = render()
    val first = handle.currentRendering
    render()
    val second = handle.currentRendering

    assertEquals(first, second)
    assertNotSame(first, second)
  }

  @Test fun renderIndirectly_delivers_child_output_to_handler() = runTest {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    val handler: (String) -> WorkflowAction<String, String, String> = { output ->
      action("") { setOutput("case output:$output") }
    }

    val handle = manager.renderIndirectly(workflow, "props", key = "", handler = handler)
    manager.commitRenderedChildren()

    val appliedActionResult = async { manager.applyNextAction() }
    assertFalse(appliedActionResult.isCompleted)

    (handle.currentRendering as Rendering).eventHandler("event!")
    val update = appliedActionResult.await().output!!.value!!

    val (_, result) = update.applyTo("props", "state")
    assertEquals("case output:workflow output:event!", result.output!!.value)
  }

  @Test fun renderIndirectly_returns_new_handle_for_new_key() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()

    val able = manager.renderIndirectly(workflow, "props", key = "able", handler = { fail() })
    val baker = manager.renderIndirectly(workflow, "props", key = "baker", handler = { fail() })
    manager.commitRenderedChildren()

    assertNotSame(able, baker)
    assertEquals(2, workflow.started)
  }

  @Test fun renderIndirectly_returns_new_handle_for_new_workflow() {
    val manager = subtreeManagerForTest<String, String, String>()

    val first = manager.renderIndirectly(TestWorkflow(), "props", key = "", handler = { fail() })
    manager.commitRenderedChildren()
    val second = manager.renderIndirectly(TestWorkflow(), "props", key = "", handler = { fail() })
    manager.commitRenderedChildren()

    // Same workflow type and key, so this is still the same session and the same handle.
    assertSame(first, second)

    val other = manager.renderIndirectly(OtherWorkflow, Unit, key = "", handler = { fail() })
    assertNotSame(first, other)
  }

  @Test fun renderIndirectly_returns_new_handle_after_child_is_torn_down() {
    val manager = subtreeManagerForTest<String, String, String>()
    val workflow = TestWorkflow()
    fun render() = manager.renderIndirectly(workflow, "props", key = "", handler = { fail() })
      .also { manager.commitRenderedChildren() }

    val first = render()

    // Render pass that doesn't render the child at all, which tears its session down.
    manager.commitRenderedChildren()

    val second = render()
    assertNotSame(first, second)
    assertEquals(2, workflow.started)
  }

  @Test fun renderWorkflowIndirectly_from_render_context() = runTest(UnconfinedTestDispatcher()) {
    val child = Workflow.stateless<String, Nothing, String> { props -> "child:$props" }
    val parent = Workflow.stateless<String, Nothing, RenderingHandle> { props ->
      renderWorkflowIndirectly(child, props)
    }
    val props = MutableStateFlow("one")

    val renderings = renderWorkflowIn(
      workflow = parent,
      scope = backgroundScope,
      props = props
    ) {}

    val handle = renderings.value.rendering
    assertEquals("child:one", handle.currentRendering)

    props.value = "two"
    advanceUntilIdle()

    // The parent gets the very same handle back, and the handle sees the child's new rendering.
    assertSame(handle, renderings.value.rendering)
    assertEquals("child:two", handle.currentRendering)
  }

  @Test fun renderIndirectly_supports_null_renderings() = runTest(UnconfinedTestDispatcher()) {
    // renderChild allows children to render null, so renderWorkflowIndirectly has to as well.
    val child = Workflow.stateless<String, Nothing, String?> { props ->
      props.takeIf { it != "one" }
    }
    val parent = Workflow.stateless<String, Nothing, RenderingHandle> { props ->
      renderWorkflowIndirectly(child, props)
    }
    val props = MutableStateFlow("one")

    val renderings = renderWorkflowIn(
      workflow = parent,
      scope = backgroundScope,
      props = props
    ) {}

    val handle = renderings.value.rendering
    assertNull(handle.currentRendering)

    props.value = "two"
    advanceUntilIdle()

    assertEquals("two", handle.currentRendering)
  }

  private object OtherWorkflow : StatefulWorkflow<Unit, Unit, String, String>() {
    override fun initialState(
      props: Unit,
      snapshot: Snapshot?
    ) = Unit

    override fun render(
      renderProps: Unit,
      renderState: Unit,
      context: RenderContext<Unit, Unit, String>
    ): String = "other"

    override fun snapshotState(state: Unit): Snapshot? = null
  }

  @Suppress("UNCHECKED_CAST")
  private suspend fun <P, S, O : Any> SubtreeManager<P, S, O>.applyNextAction() =
    select<ActionProcessingResult?> {
      registerChildActionSelectors(this)
    } as ActionApplied<WorkflowAction<P, S, O>?>

  private fun <P, S, O : Any> subtreeManagerForTest(
    snapshotCache: Map<WorkflowNodeId, TreeSnapshot>? = null,
    runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  ) = SubtreeManager<P, S, O>(
    snapshotCache = snapshotCache,
    contextForChildren = Unconfined,
    runtimeConfig = runtimeConfig,
    emitActionToParent = { action, childResult ->
      ActionApplied(WorkflowOutput(action), childResult.stateChanged)
    },
    workflowTracer = null
  )
}
