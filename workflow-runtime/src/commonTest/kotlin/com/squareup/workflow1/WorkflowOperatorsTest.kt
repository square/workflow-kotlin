package com.squareup.workflow1

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class, FlowPreview::class)
class WorkflowOperatorsTest {

  @Test fun mapRendering_toString() {
    val workflow = object : StatelessWorkflow<Unit, Nothing, Nothing>() {
      override fun toString(): String = "ChildWorkflow"
      override fun render(
        renderProps: Unit,
        context: RenderContext
      ): Nothing = fail()
    }
    val mappedWorkflow = workflow.mapRendering { fail() }

    assertEquals("ChildWorkflow.mapRendering()", mappedWorkflow.toString())
  }

  @Test fun mapRendering_transforms_rendering() {
    val trigger = MutableStateFlow("initial")
    val childWorkflow = object : StateFlowWorkflow<String>("child", trigger) {}
    val mappedWorkflow = childWorkflow.mapRendering { "mapped: $it" }

    runTest(UnconfinedTestDispatcher()) {
      val renderings = mutableListOf<String>()
      val workflowJob = Job(coroutineContext[Job])
      renderWorkflowIn(mappedWorkflow, this + workflowJob, MutableStateFlow(Unit)) {}
        .onEach { renderings += it.rendering }
        .launchIn(this + workflowJob)
      assertEquals(listOf("mapped: initial"), renderings)

      trigger.value = "foo"
      assertEquals(listOf("mapped: initial", "mapped: foo"), renderings)

      trigger.value = "bar"
      assertEquals(listOf("mapped: initial", "mapped: foo", "mapped: bar"), renderings)

      workflowJob.cancel()
    }
  }

  @Test fun mapRendering_on_two_different_upstream_workflows_both_render() {
    val trigger1 = MutableStateFlow("initial1")
    val trigger2 = MutableStateFlow("initial2")
    val child1 = object : StateFlowWorkflow<String>("child1", trigger1) {}
    val child2 = object : StateFlowWorkflow<String>("child2", trigger2) {}
    val parentWorkflow = Workflow.stateless<Unit, String, String> {
      listOf(
        renderChild(child1.mapRendering { "rendering1: $it" }),
        renderChild(child2.mapRendering { "rendering2: $it" })
      ).toString()
    }

    runTest(UnconfinedTestDispatcher()) {
      val renderings = mutableListOf<String>()
      val workflowJob = Job(coroutineContext[Job])
      renderWorkflowIn(parentWorkflow, this + workflowJob, MutableStateFlow(Unit)) {}
        .onEach { renderings += it.rendering }
        .launchIn(this + workflowJob)
      assertEquals(
        listOf(
          "[rendering1: initial1, rendering2: initial2]"
        ),
        renderings
      )

      trigger1.value = "foo"
      assertEquals(
        listOf(
          "[rendering1: initial1, rendering2: initial2]",
          "[rendering1: foo, rendering2: initial2]"
        ),
        renderings
      )

      trigger2.value = "bar"
      assertEquals(
        listOf(
          "[rendering1: initial1, rendering2: initial2]",
          "[rendering1: foo, rendering2: initial2]",
          "[rendering1: foo, rendering2: bar]"
        ),
        renderings
      )

      workflowJob.cancel()
    }
  }

  @Test fun mapRendering_with_upstream_workflow_both_render() {
    val trigger1 = MutableStateFlow("initial1")
    val trigger2 = MutableStateFlow("initial2")
    val child1 = object : StateFlowWorkflow<String>("child1", trigger1) {}
    val child2 = object : StateFlowWorkflow<String>("child2", trigger2) {}
    val parentWorkflow = Workflow.stateless<Unit, Nothing, String> {
      listOf(
        renderChild(child1),
        renderChild(child2.mapRendering { "rendering2: $it" })
      ).toString()
    }

    runTest(UnconfinedTestDispatcher()) {
      val renderings = mutableListOf<String>()
      val workflowJob = Job(coroutineContext[Job])
      renderWorkflowIn(parentWorkflow, this + workflowJob, MutableStateFlow(Unit)) {}
        .onEach { renderings += it.rendering }
        .launchIn(this + workflowJob)
      assertEquals(
        listOf(
          "[initial1, rendering2: initial2]"
        ),
        renderings
      )

      trigger1.value = "foo"
      assertEquals(
        listOf(
          "[initial1, rendering2: initial2]",
          "[foo, rendering2: initial2]"
        ),
        renderings
      )

      trigger2.value = "bar"
      assertEquals(
        listOf(
          "[initial1, rendering2: initial2]",
          "[foo, rendering2: initial2]",
          "[foo, rendering2: bar]"
        ),
        renderings
      )

      workflowJob.cancel()
    }
  }

  @Test
  fun mapRendering_with_same_upstream_workflow_in_two_different_passes_does_not_restart() {
    val trigger = MutableStateFlow("initial")
    val childWorkflow = object : StateFlowWorkflow<String>("child", trigger) {}
    val parentWorkflow = Workflow.stateless<Int, Nothing, String> { props ->
      when (props) {
        // I don't understand this test. [mapRendering] defers to its wrapped Workflow for the identifier
        // which is equivalent - but we are relying on the fact that we pass in the Workflow each time we render
        // so we pull out the *same* child WorkflowNode (starts = 1) but give it a new Workflow to render.
        // Why?
        0 -> renderChild(childWorkflow.mapRendering { "rendering1: $it" })
        1 -> renderChild(childWorkflow.mapRendering { "rendering2: $it" })
        else -> fail()
      }
    }
    val props = MutableStateFlow(0)

    runTest {
      val renderings = mutableListOf<String>()
      val workflowJob = Job(coroutineContext[Job])
      renderWorkflowIn(parentWorkflow, this + workflowJob, props) {}
        .onEach { renderings += it.rendering }
        .launchIn(this + workflowJob)
      runCurrent()
      assertEquals(
        listOf(
          "rendering1: initial"
        ),
        renderings
      )
      assertEquals(1, childWorkflow.starts)

      trigger.value = "foo"
      runCurrent()
      assertEquals(1, childWorkflow.starts)
      assertEquals(
        listOf(
          "rendering1: initial",
          "rendering1: foo"
        ),
        renderings
      )

      props.value = 1
      runCurrent()
      trigger.value = "bar"
      runCurrent()
      assertEquals(1, childWorkflow.starts)
      assertEquals(
        listOf(
          "rendering1: initial",
          "rendering1: foo",
          "rendering2: foo",
          "rendering2: bar"
        ),
        renderings
      )

      workflowJob.cancel()
    }
  }

  private abstract class StateFlowWorkflow<T>(
    val name: String,
    val flow: StateFlow<T>
  ) : StatelessWorkflow<Unit, Nothing, T>() {
    var starts: Int = 0
      private set

    private val rerenderWorker = object : Worker<T> {
      override fun run(): Flow<T> = flow.onStart { starts++ }
    }

    override fun render(
      renderProps: Unit,
      context: RenderContext
    ): T {
      // Listen to the flow to trigger a re-render when it updates.
      context.runningWorker(rerenderWorker as Worker<Any?>) { WorkflowAction.noAction() }
      return flow.value
    }

    override fun toString(): String = "StateFlowWorkflow($name)"
  }
}
