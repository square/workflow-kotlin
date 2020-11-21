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
import kotlinx.coroutines.test.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class, FlowPreview::class)
class WorkflowOperatorsTest {

  @Test fun `mapRendering toString`() {
    val workflow = object : StatelessWorkflow<Unit, Nothing, Nothing>() {
      override fun toString(): String = "ChildWorkflow"
      override fun render(
        props: Unit,
        context: RenderContext
      ): Nothing = fail()
    }
    val mappedWorkflow = workflow.mapRendering { fail() }

    assertEquals("ChildWorkflow.mapRendering()", mappedWorkflow.toString())
  }

  @Test fun `mapRendering transforms rendering`() {
    val trigger = MutableStateFlow("initial")
    val childWorkflow = object : StateFlowWorkflow<String>("child", trigger) {}
    val mappedWorkflow = childWorkflow.mapRendering { "mapped: $it" }

    runBlockingTest {
      val renderings = mutableListOf<String>()
      val workflowJob = Job(coroutineContext[Job])
      renderWorkflowIn(mappedWorkflow, this + workflowJob, MutableStateFlow(Unit)) {}
          .onEach { renderings += it.rendering }
          .launchIn(this + workflowJob)
      assertEquals(listOf("mapped: initial"), renderings)

      trigger.value = "foo"
      advanceUntilIdle()
      assertEquals(listOf("mapped: initial", "mapped: foo"), renderings)

      trigger.value = "bar"
      advanceUntilIdle()
      assertEquals(listOf("mapped: initial", "mapped: foo", "mapped: bar"), renderings)

      workflowJob.cancel()
    }
  }

  @Test fun `mapRendering on two different upstream workflows both render`() {
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

    runBlockingTest {
      val renderings = mutableListOf<String>()
      val workflowJob = Job(coroutineContext[Job])
      renderWorkflowIn(parentWorkflow, this + workflowJob, MutableStateFlow(Unit)) {}
          .onEach { renderings += it.rendering }
          .launchIn(this + workflowJob)
      assertEquals(
          listOf(
              "[rendering1: initial1, rendering2: initial2]"
          ), renderings
      )

      trigger1.value = "foo"
      advanceUntilIdle()
      assertEquals(
          listOf(
              "[rendering1: initial1, rendering2: initial2]",
              "[rendering1: foo, rendering2: initial2]"
          ), renderings
      )

      trigger2.value = "bar"
      advanceUntilIdle()
      assertEquals(
          listOf(
              "[rendering1: initial1, rendering2: initial2]",
              "[rendering1: foo, rendering2: initial2]",
              "[rendering1: foo, rendering2: bar]"
          ), renderings
      )

      workflowJob.cancel()
    }
  }

  @Test fun `mapRendering with upstream workflow both render`() {
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

    runBlockingTest {
      val renderings = mutableListOf<String>()
      val workflowJob = Job(coroutineContext[Job])
      renderWorkflowIn(parentWorkflow, this + workflowJob, MutableStateFlow(Unit)) {}
          .onEach { renderings += it.rendering }
          .launchIn(this + workflowJob)
      assertEquals(
          listOf(
              "[initial1, rendering2: initial2]"
          ), renderings
      )

      trigger1.value = "foo"
      advanceUntilIdle()
      assertEquals(
          listOf(
              "[initial1, rendering2: initial2]",
              "[foo, rendering2: initial2]"
          ), renderings
      )

      trigger2.value = "bar"
      advanceUntilIdle()
      assertEquals(
          listOf(
              "[initial1, rendering2: initial2]",
              "[foo, rendering2: initial2]",
              "[foo, rendering2: bar]"
          ), renderings
      )

      workflowJob.cancel()
    }
  }

  @Test fun `mapRendering with same upstream workflow in two different passes doesn't restart`() {
    val trigger = MutableStateFlow("initial")
    val childWorkflow = object : StateFlowWorkflow<String>("child", trigger) {}
    val parentWorkflow = Workflow.stateless<Int, Nothing, String> { props ->
      when (props) {
        0 -> renderChild(childWorkflow.mapRendering { "rendering1: $it" })
        1 -> renderChild(childWorkflow.mapRendering { "rendering2: $it" })
        else -> fail()
      }
    }
    val props = MutableStateFlow(0)

    runBlockingTest {
      val renderings = mutableListOf<String>()
      val workflowJob = Job(coroutineContext[Job])
      renderWorkflowIn(parentWorkflow, this + workflowJob, props) {}
          .onEach { renderings += it.rendering }
          .launchIn(this + workflowJob)
      assertEquals(
          listOf(
              "rendering1: initial"
          ), renderings
      )
      assertEquals(1, childWorkflow.starts)

      trigger.value = "foo"
      advanceUntilIdle()
      assertEquals(1, childWorkflow.starts)
      assertEquals(
          listOf(
              "rendering1: initial",
              "rendering1: foo"
          ), renderings
      )

      props.value = 1
      trigger.value = "bar"
      advanceUntilIdle()
      assertEquals(1, childWorkflow.starts)
      assertEquals(
          listOf(
              "rendering1: initial",
              "rendering1: foo",
              "rendering2: foo",
              "rendering2: bar"
          ), renderings
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
      props: Unit,
      context: RenderContext
    ): T {
      // Listen to the flow to trigger a re-render when it updates.
      context.runningWorker(rerenderWorker as Worker<Any?>) { WorkflowAction.noAction() }
      return flow.value
    }

    override fun toString(): String = "StateFlowWorkflow($name)"
  }
}
