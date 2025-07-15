package com.squareup.benchmark.runtime.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.tracing.Trace
import app.cash.burst.Burst
import com.squareup.benchmark.runtime.benchmark.BenchmarkRuntimeOptions.NONE
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.renderWorkflowIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private const val MaxChildCount = 100

/** The microbenchmarks take a while to run, so we only run with a subset of runtime configs. */
@Suppress("unused")
@OptIn(WorkflowExperimentalRuntime::class)
enum class BenchmarkRuntimeOptions(
  val runtimeConfig: RuntimeConfig
) {
  NONE(RuntimeOptions.NONE.runtimeConfig),
  ALL(RuntimeOptions.ALL.runtimeConfig),
}

@OptIn(WorkflowExperimentalRuntime::class)
@Burst
class ComposeRuntimeMicrobenchmark(
  private val runtime: BenchmarkRuntimeOptions = NONE,
) {

  @get:Rule val benchmarkRule = BenchmarkRule()

  @Test fun initialRenderAllChildren() = benchmarkShallowWideWorkflowPropsChange(
    childCount = MaxChildCount,
    setupProps = ShallowWideWorkflowRoot.Props(
      renderFirstChild = false,
      renderOtherChildren = false,
    ),
    testProps = ShallowWideWorkflowRoot.Props(
      renderFirstChild = true,
      renderOtherChildren = true,
      firstChildProps = 1,
      otherChildrenProps = 1,
    ),
    expectedSetupRendering = 0,
    expectedTestRendering = MaxChildCount
  )

  @Test fun initialRenderNewSibling() = benchmarkShallowWideWorkflowPropsChange(
    childCount = MaxChildCount,
    setupProps = ShallowWideWorkflowRoot.Props(
      renderFirstChild = false,
      renderOtherChildren = true,
      otherChildrenProps = 1,
    ),
    testProps = ShallowWideWorkflowRoot.Props(
      renderFirstChild = true,
      renderOtherChildren = true,
      firstChildProps = 1,
      otherChildrenProps = 1,
    ),
    expectedSetupRendering = MaxChildCount - 1,
    expectedTestRendering = MaxChildCount
  )

  @Test fun tearDownAllChildren() = benchmarkShallowWideWorkflowPropsChange(
    childCount = MaxChildCount,
    setupProps = ShallowWideWorkflowRoot.Props(
      renderFirstChild = true,
      renderOtherChildren = true,
      firstChildProps = 1,
      otherChildrenProps = 1,
    ),
    testProps = ShallowWideWorkflowRoot.Props(
      renderFirstChild = false,
      renderOtherChildren = false,
    ),
    expectedSetupRendering = MaxChildCount,
    expectedTestRendering = 0
  )

  @Test fun tearDownSingleSibling() = benchmarkShallowWideWorkflowPropsChange(
    childCount = MaxChildCount,
    setupProps = ShallowWideWorkflowRoot.Props(
      renderFirstChild = true,
      renderOtherChildren = true,
      firstChildProps = 1,
      otherChildrenProps = 1,
    ),
    testProps = ShallowWideWorkflowRoot.Props(
      renderFirstChild = false,
      renderOtherChildren = true,
      otherChildrenProps = 1,
    ),
    expectedSetupRendering = MaxChildCount,
    expectedTestRendering = MaxChildCount - 1
  )

  @Test fun rerenderAllChildrenByPropsChange() = benchmarkShallowWideWorkflowPropsChange(
    childCount = MaxChildCount,
    setupProps = ShallowWideWorkflowRoot.Props(
      firstChildProps = 1,
      otherChildrenProps = 1,
    ),
    testProps = ShallowWideWorkflowRoot.Props(
      firstChildProps = 2,
      otherChildrenProps = 2,
    ),
    expectedSetupRendering = MaxChildCount,
    expectedTestRendering = MaxChildCount * 2
  )

  @Test fun rerenderSingleSiblingByPropsChange() = benchmarkShallowWideWorkflowPropsChange(
    childCount = MaxChildCount,
    setupProps = ShallowWideWorkflowRoot.Props(
      firstChildProps = 1,
      otherChildrenProps = 1,
    ),
    testProps = ShallowWideWorkflowRoot.Props(
      firstChildProps = 2,
      otherChildrenProps = 1,
    ),
    expectedSetupRendering = MaxChildCount,
    expectedTestRendering = MaxChildCount + 1
  )

  private fun benchmarkShallowWideWorkflowPropsChange(
    childCount: Int,
    setupProps: ShallowWideWorkflowRoot.Props,
    testProps: ShallowWideWorkflowRoot.Props,
    expectedSetupRendering: Int,
    expectedTestRendering: Int,
  ) = runTest {
    val workflow = ShallowWideWorkflowRoot(childCount = childCount)
    val props = MutableStateFlow(setupProps)
    val workflowJob = Job(parent = coroutineContext.job)
    val renderings = renderWorkflowIn(
      workflow = workflow,
      props = props,
      scope = this + workflowJob,
      runtimeConfig = runtime.runtimeConfig,
      workflowTracer = Tracer,
      onOutput = {}
    )

    benchmarkRule.measureRepeated {
      runWithTimingDisabled {
        // Clear the workflow tree.
        props.value = setupProps
        testScheduler.runCurrent()
        assertEquals(expectedSetupRendering, renderings.value.rendering)
      }

      // Writing a new props value schedules the render pass that will set everything up.
      props.value = testProps
      // Run the render pass.
      testScheduler.runCurrent()

      assertEquals(expectedTestRendering, renderings.value.rendering)
    }
    workflowJob.cancel()
  }

  private fun benchmarkShallowWideWorkflowStateChange(
    childCount: Int,
    testState: (setStateForChild: (Int, Int) -> Unit) -> Unit,
    expectedTestRendering: Int,
  ) = runTest {
    val actionSinks = arrayOfNulls<Sink<WorkflowAction<*, Int, Nothing>>?>(childCount)
    val workflow = ShallowWideWorkflowRoot(
      childCount = childCount,
      actionSinks = actionSinks
    )
    val props = MutableStateFlow(ShallowWideWorkflowRoot.Props())
    val workflowJob = Job(parent = coroutineContext.job)
    val renderings = renderWorkflowIn(
      workflow = workflow,
      props = props,
      scope = this + workflowJob,
      runtimeConfig = runtime.runtimeConfig,
      workflowTracer = Tracer,
      onOutput = {}
    )

    val resetStateAction = action<Any?, Int, Nothing>("resetState") { this.state = 0 }

    benchmarkRule.measureRepeated {
      runWithTimingDisabled {
        // Clear the workflow tree.
        actionSinks.forEach { sink ->
          sink!!.send(resetStateAction)
        }
        testScheduler.runCurrent()
        assertEquals(0, renderings.value.rendering)

        // We don't care about measuring the actual sending of actions into the sinks. The render
        // won't actually happen until we advance the test scheduler below.
        testState { index, newState ->
          actionSinks[index]!!.send(
            action<Any?, Int, Nothing>("setState") {
              this.state = newState
            }
          )
        }
      }

      // Run the render pass.
      testScheduler.runCurrent()

      assertEquals(expectedTestRendering, renderings.value.rendering)
    }
    workflowJob.cancel()
  }

  @Test fun rerenderSingleSiblingViaStateChange() = benchmarkShallowWideWorkflowStateChange(
    childCount = MaxChildCount,
    testState = { setStateForChild -> setStateForChild(MaxChildCount / 2, 1) },
    expectedTestRendering = 1,
  )

  @Test fun rerenderAllChildrenViaStateChange() = benchmarkShallowWideWorkflowStateChange(
    childCount = MaxChildCount,
    testState = { setStateForChild ->
      repeat(MaxChildCount) {
        setStateForChild(it, 1)
      }
    },
    expectedTestRendering = MaxChildCount,
  )
}

private object Tracer : WorkflowTracer {
  override fun beginSection(label: String) = Trace.beginSection(label)
  override fun endSection() = Trace.endSection()
}

private class ShallowWideWorkflowRoot(
  private val childCount: Int,
  private val actionSinks: Array<Sink<WorkflowAction<*, Int, Nothing>>?>? = null,
) : StatelessWorkflow<ShallowWideWorkflowRoot.Props, Nothing, Int>() {
  data class Props(
    val renderFirstChild: Boolean = true,
    val renderOtherChildren: Boolean = true,
    val firstChildProps: Int = 0,
    val otherChildrenProps: Int = 0,
  )

  private val child = Child()

  override fun render(
    renderProps: Props,
    context: RenderContext<Props, Nothing>
  ): Int {
    var rendering = 0
    repeat(childCount) { childIndex ->
      if (childIndex == 0 && renderProps.renderFirstChild) {
        rendering += context.renderChild(
          child = child,
          key = childIndex.toString(),
          props = ChildProps(index = childIndex, propsValue = renderProps.firstChildProps)
        )
      } else if (childIndex > 0 && renderProps.renderOtherChildren) {
        rendering += context.renderChild(
          child = child,
          key = childIndex.toString(),
          props = ChildProps(index = childIndex, propsValue = renderProps.otherChildrenProps)
        )
      }
    }
    return rendering
  }

  private data class ChildProps(
    val index: Int,
    val propsValue: Int,
  )

  private inner class Child : StatefulWorkflow<ChildProps, Int, Nothing, Int>() {
    override fun initialState(
      props: ChildProps,
      snapshot: Snapshot?
    ): Int = 0

    override fun render(
      renderProps: ChildProps,
      renderState: Int,
      context: RenderContext<ChildProps, Int, Nothing>
    ): Int {
      if (actionSinks != null) {
        @Suppress("UNCHECKED_CAST")
        actionSinks[renderProps.index] = context.actionSink as Sink<WorkflowAction<*, Int, Nothing>>
      }
      return renderProps.propsValue + renderState
    }

    override fun snapshotState(state: Int): Snapshot? = null
  }
}
