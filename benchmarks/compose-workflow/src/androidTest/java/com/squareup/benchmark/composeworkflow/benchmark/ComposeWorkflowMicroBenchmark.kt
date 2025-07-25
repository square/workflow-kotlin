@file:OptIn(WorkflowExperimentalApi::class)

package com.squareup.benchmark.composeworkflow.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.composable
import com.squareup.workflow1.compose.renderChild
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.stateless
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@OptIn(WorkflowExperimentalRuntime::class)
@RunWith(AndroidJUnit4::class)
class ComposeWorkflowMicroBenchmark {

  @get:Rule val benchmarkRule = BenchmarkRule()

  @Test fun tradRoot_tradChildren_initialRender() {
    benchmarkSimpleTreeInitialRender(
      maxChildCount = 100,
      composeRoot = false,
      composeChildren = false
    )
  }

  @Test fun tradRoot_composeChildren_initialRender() {
    benchmarkSimpleTreeInitialRender(
      maxChildCount = 100,
      composeRoot = false,
      composeChildren = true
    )
  }

  @Test fun composeRoot_tradChildren_initialRender() {
    benchmarkSimpleTreeInitialRender(
      maxChildCount = 100,
      composeRoot = true,
      composeChildren = false
    )
  }

  @Test fun composeRoot_composeChildren_initialRender() {
    benchmarkSimpleTreeInitialRender(
      maxChildCount = 100,
      composeRoot = true,
      composeChildren = true
    )
  }

  private fun benchmarkSimpleTreeInitialRender(
    maxChildCount: Int,
    composeRoot: Boolean,
    composeChildren: Boolean
  ) = runTest {
    val props =
      MutableStateFlow(RootWorkflowProps(childCount = 0, composeChildren = composeChildren))
    val workflowJob = Job(parent = coroutineContext.job)
    val renderings = renderWorkflowIn(
      workflow = if (composeRoot) {
        composeSimpleRoot
      } else {
        traditionalSimpleRoot
      },
      props = props,
      scope = this + workflowJob,
      runtimeConfig = RuntimeConfigOptions.ALL,
      onOutput = {}
    )

    benchmarkRule.measureRepeated {
      runWithTimingDisabled {
        props.value = RootWorkflowProps(childCount = 0, composeChildren = composeChildren)
        testScheduler.runCurrent()
        assertEquals(0, renderings.value.rendering)
      }

      props.value = RootWorkflowProps(childCount = maxChildCount, composeChildren = composeChildren)
      testScheduler.runCurrent()
      assertEquals(maxChildCount, renderings.value.rendering)
    }

    workflowJob.cancel()
  }
}

private data class RootWorkflowProps(
  val childCount: Int,
  val composeChildren: Boolean
)

private val traditionalSimpleRoot = Workflow.stateless<RootWorkflowProps, Nothing, Int> { props ->
  var rendering = 0
  repeat(props.childCount) { child ->
    rendering += renderChild(
      key = child.toString(),
      child = if (props.composeChildren) {
        composeSimpleLeaf
      } else {
        traditionalSimpleLeaf
      }
    )
  }
  rendering
}

private val composeSimpleRoot = Workflow.composable<RootWorkflowProps, Nothing, Int> { props, _ ->
  var rendering = 0
  repeat(props.childCount) {
    rendering += renderChild(
      workflow = if (props.composeChildren) {
        composeSimpleLeaf
      } else {
        traditionalSimpleLeaf
      },
      props = Unit,
      onOutput = null
    )
  }
  rendering
}

private val traditionalSimpleLeaf = Workflow.stateless<Unit, Nothing, Int> { 1 }
private val composeSimpleLeaf = Workflow.composable<Unit, Nothing, Int> { _, _ -> 1 }
