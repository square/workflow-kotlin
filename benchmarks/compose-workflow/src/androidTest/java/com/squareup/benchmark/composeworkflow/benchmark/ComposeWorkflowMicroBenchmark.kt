@file:OptIn(WorkflowExperimentalApi::class)

package com.squareup.benchmark.composeworkflow.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tracing.Trace
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowTracer
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

private const val MaxChildCount = 100

@OptIn(WorkflowExperimentalRuntime::class)
@RunWith(AndroidJUnit4::class)
class ComposeWorkflowMicroBenchmark {

  @get:Rule val benchmarkRule = BenchmarkRule()

  @Test fun tradRoot_tradChildren_initialRender() {
    benchmarkSimpleTreeInitialRender(
      composeRoot = false,
      composeChildren = false
    )
  }

  @Test fun tradRoot_composeChildren_initialRender() {
    benchmarkSimpleTreeInitialRender(
      composeRoot = false,
      composeChildren = true
    )
  }

  @Test fun composeRoot_tradChildren_initialRender() {
    benchmarkSimpleTreeInitialRender(
      composeRoot = true,
      composeChildren = false
    )
  }

  @Test fun composeRoot_composeChildren_initialRender() {
    benchmarkSimpleTreeInitialRender(
      composeRoot = true,
      composeChildren = true
    )
  }

  @Test fun tradRoot_tradChildren_tearDown() {
    benchmarkSimpleTreeTearDown(
      composeRoot = false,
      composeChildren = false
    )
  }

  @Test fun tradRoot_composeChildren_tearDown() {
    benchmarkSimpleTreeTearDown(
      composeRoot = false,
      composeChildren = true
    )
  }

  @Test fun composeRoot_tradChildren_tearDown() {
    benchmarkSimpleTreeTearDown(
      composeRoot = true,
      composeChildren = false
    )
  }

  @Test fun composeRoot_composeChildren_tearDown() {
    benchmarkSimpleTreeTearDown(
      composeRoot = true,
      composeChildren = true
    )
  }

  @Test fun tradRoot_tradChildren_subsequentRender() {
    benchmarkSimpleTreeSubsequentRender(
      composeRoot = false,
      composeChildren = false
    )
  }

  @Test fun tradRoot_composeChildren_subsequentRender() {
    benchmarkSimpleTreeSubsequentRender(
      composeRoot = false,
      composeChildren = true
    )
  }

  @Test fun composeRoot_tradChildren_subsequentRender() {
    benchmarkSimpleTreeSubsequentRender(
      composeRoot = true,
      composeChildren = false
    )
  }

  @Test fun composeRoot_composeChildren_subsequentRender() {
    benchmarkSimpleTreeSubsequentRender(
      composeRoot = true,
      composeChildren = true
    )
  }

  private fun benchmarkSimpleTreeInitialRender(
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
      workflowTracer = Tracer,
      onOutput = {}
    )

    benchmarkRule.measureRepeated {
      runWithTimingDisabled {
        props.value = RootWorkflowProps(childCount = 0, composeChildren = composeChildren)
        testScheduler.runCurrent()
        assertEquals(0, renderings.value.rendering)
      }

      props.value = RootWorkflowProps(childCount = MaxChildCount, composeChildren = composeChildren)
      testScheduler.runCurrent()
      assertEquals(MaxChildCount, renderings.value.rendering)
    }

    workflowJob.cancel()
  }

  private fun benchmarkSimpleTreeTearDown(
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
      workflowTracer = Tracer,
      onOutput = {}
    )

    benchmarkRule.measureRepeated {
      runWithTimingDisabled {
        props.value =
          RootWorkflowProps(childCount = MaxChildCount, composeChildren = composeChildren)
        testScheduler.runCurrent()
        assertEquals(MaxChildCount, renderings.value.rendering)
      }

      props.value = RootWorkflowProps(childCount = 0, composeChildren = composeChildren)
      testScheduler.runCurrent()
      assertEquals(0, renderings.value.rendering)
    }

    workflowJob.cancel()
  }

  private fun benchmarkSimpleTreeSubsequentRender(
    composeRoot: Boolean,
    composeChildren: Boolean
  ) = runTest {
    val props = MutableStateFlow(
      RootWorkflowProps(
        childCount = MaxChildCount,
        composeChildren = composeChildren
      )
    )
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
      workflowTracer = Tracer,
      onOutput = {}
    )

    benchmarkRule.measureRepeated {
      runWithTimingDisabled {
        props.value =
          RootWorkflowProps(
            childCount = MaxChildCount,
            composeChildren = composeChildren,
            childProps = 1
          )
        testScheduler.runCurrent()
        assertEquals(MaxChildCount, renderings.value.rendering)
      }

      props.value = RootWorkflowProps(
        childCount = MaxChildCount,
        composeChildren = composeChildren,
        childProps = 2
      )
      testScheduler.runCurrent()
      assertEquals(MaxChildCount * 2, renderings.value.rendering)
    }

    workflowJob.cancel()
  }
}

private object Tracer : WorkflowTracer {
  override fun beginSection(label: String) {
    Trace.beginSection(label)
  }

  override fun endSection() {
    Trace.endSection()
  }
}

private data class RootWorkflowProps(
  val childCount: Int,
  val composeChildren: Boolean,
  val childProps: Int = 1,
)

private val traditionalSimpleRoot = Workflow.stateless<RootWorkflowProps, Nothing, Int> { props ->
  var rendering = 0
  repeat(props.childCount) { child ->
    rendering += renderChild(
      key = child.toString(),
      props = props.childProps,
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
      props = props.childProps,
      workflow = if (props.composeChildren) {
        composeSimpleLeaf
      } else {
        traditionalSimpleLeaf
      },
    )
  }
  rendering
}

private val traditionalSimpleLeaf = Workflow.stateless<Int, Nothing, Int> { it }
private val composeSimpleLeaf = Workflow.composable<Int, Nothing, Int> { props, _ -> props }
