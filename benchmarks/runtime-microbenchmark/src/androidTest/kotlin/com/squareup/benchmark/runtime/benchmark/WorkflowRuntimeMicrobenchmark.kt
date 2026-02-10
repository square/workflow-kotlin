package com.squareup.benchmark.runtime.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.tracing.Trace
import app.cash.burst.Burst
import com.squareup.benchmark.runtime.benchmark.BenchmarkRuntimeOptions.NoOptimizations
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
import com.squareup.workflow1.remember
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.renderWorkflowIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.math.pow
import kotlin.test.assertEquals

/** The microbenchmarks take a while to run, so we only run with a subset of runtime configs. */
@Suppress("unused")
@OptIn(WorkflowExperimentalRuntime::class)
enum class BenchmarkRuntimeOptions(
  val runtimeConfig: RuntimeConfig
) {
  NoOptimizations(RuntimeOptions.NONE.runtimeConfig),
  AllOptimizations(RuntimeOptions.ALL.runtimeConfig),
}

enum class BenchmarkTreeShape(
  val degree: Int,
  val depth: Int,
) {
  ShallowBushyTree(degree = 75, depth = 1),
  SquareishTree(degree = 3, depth = 5),
}

@OptIn(WorkflowExperimentalRuntime::class)
@Burst
class WorkflowRuntimeMicrobenchmark(
  private val treeShape: BenchmarkTreeShape = BenchmarkTreeShape.ShallowBushyTree,
  private val runtime: BenchmarkRuntimeOptions = NoOptimizations,
) {

  @get:Rule val benchmarkRule = BenchmarkRule()

  @Test fun initialRenderAllChildren() = benchmarkWorkflowPropsChange(
    setupProps = BenchmarkWorkflowRoot.Props(
      renderFirstLeaf = false,
      renderOtherLeaves = false,
    ),
    testProps = BenchmarkWorkflowRoot.Props(
      renderFirstLeaf = true,
      renderOtherLeaves = true,
      firstLeafProps = 1,
      otherLeafProps = 1,
    ),
    expectedSetupRendering = 0,
    expectedTestRendering = treeShape.leafCount
  )

  @Test fun initialRenderNewSibling() = benchmarkWorkflowPropsChange(
    setupProps = BenchmarkWorkflowRoot.Props(
      renderFirstLeaf = false,
      renderOtherLeaves = true,
      otherLeafProps = 1,
    ),
    testProps = BenchmarkWorkflowRoot.Props(
      renderFirstLeaf = true,
      renderOtherLeaves = true,
      firstLeafProps = 1,
      otherLeafProps = 1,
    ),
    expectedSetupRendering = treeShape.leafCount - 1,
    expectedTestRendering = treeShape.leafCount
  )

  @Test fun tearDownAllChildren() = benchmarkWorkflowPropsChange(
    setupProps = BenchmarkWorkflowRoot.Props(
      renderFirstLeaf = true,
      renderOtherLeaves = true,
      firstLeafProps = 1,
      otherLeafProps = 1,
    ),
    testProps = BenchmarkWorkflowRoot.Props(
      renderFirstLeaf = false,
      renderOtherLeaves = false,
    ),
    expectedSetupRendering = treeShape.leafCount,
    expectedTestRendering = 0
  )

  @Test fun tearDownSingleSibling() = benchmarkWorkflowPropsChange(
    setupProps = BenchmarkWorkflowRoot.Props(
      renderFirstLeaf = true,
      renderOtherLeaves = true,
      firstLeafProps = 1,
      otherLeafProps = 1,
    ),
    testProps = BenchmarkWorkflowRoot.Props(
      renderFirstLeaf = false,
      renderOtherLeaves = true,
      otherLeafProps = 1,
    ),
    expectedSetupRendering = treeShape.leafCount,
    expectedTestRendering = treeShape.leafCount - 1
  )

  @Test fun rerenderAllChildrenByPropsChange() = benchmarkWorkflowPropsChange(
    setupProps = BenchmarkWorkflowRoot.Props(
      firstLeafProps = 1,
      otherLeafProps = 1,
    ),
    testProps = BenchmarkWorkflowRoot.Props(
      firstLeafProps = 2,
      otherLeafProps = 2,
    ),
    expectedSetupRendering = treeShape.leafCount,
    expectedTestRendering = treeShape.leafCount * 2
  )

  @Test fun rerenderSingleSiblingByPropsChange() = benchmarkWorkflowPropsChange(
    setupProps = BenchmarkWorkflowRoot.Props(
      firstLeafProps = 1,
      otherLeafProps = 1,
    ),
    testProps = BenchmarkWorkflowRoot.Props(
      firstLeafProps = 2,
      otherLeafProps = 1,
    ),
    expectedSetupRendering = treeShape.leafCount,
    expectedTestRendering = treeShape.leafCount + 1
  )

  @Test fun rerenderSingleSiblingViaStateChange() = benchmarkWorkflowStateChange(
    // Set the first leaf's state to 1, leaving the rest at 0.
    testState = { setStateForChild -> setStateForChild(0, 1) },
    expectedTestRendering = 1,
  )

  @Test fun rerenderAllChildrenViaStateChange() = benchmarkWorkflowStateChange(
    testState = { setStateForChild ->
      repeat(treeShape.leafCount) {
        setStateForChild(it, 1)
      }
    },
    expectedTestRendering = treeShape.leafCount,
  )

  private fun benchmarkWorkflowPropsChange(
    setupProps: BenchmarkWorkflowRoot.Props,
    testProps: BenchmarkWorkflowRoot.Props,
    expectedSetupRendering: Int,
    expectedTestRendering: Int,
  ) = runTest {
    val workflow = BenchmarkWorkflowRoot(treeShape = treeShape)
    val props = MutableStateFlow(setupProps)
    val workflowJob = Job(parent = coroutineContext.job)
    val renderings = renderWorkflowIn(
      workflow = workflow,
      props = props,
      scope = this + workflowJob,
      runtimeConfig = runtime.runtimeConfig,
      workflowTracer = SystemTracer,
      onOutput = {}
    )

    benchmarkRule.measureRepeated {
      runWithTimingDisabled {
        // Clear the workflow tree.
        props.value = setupProps
        testScheduler.advanceUntilIdle()
        assertEquals(expectedSetupRendering, renderings.value.rendering)
      }

      // Writing a new props value schedules the render pass that will set everything up.
      props.value = testProps
      // Run the render pass.
      testScheduler.advanceUntilIdle()
    }

    assertEquals(expectedTestRendering, renderings.value.rendering)
    workflowJob.cancel()
  }

  private fun benchmarkWorkflowStateChange(
    testState: (setStateForChild: (index: Int, newState: Int) -> Unit) -> Unit,
    expectedTestRendering: Int,
  ) = runTest {
    val actionSinks = arrayOfNulls<Sink<WorkflowAction<*, Int, Nothing>>?>(treeShape.leafCount)
    val workflow = BenchmarkWorkflowRoot(
      treeShape = treeShape,
      actionSinks = actionSinks,
    )
    val props = MutableStateFlow(BenchmarkWorkflowRoot.Props())
    val workflowJob = Job(parent = coroutineContext.job)
    val renderings = renderWorkflowIn(
      workflow = workflow,
      props = props,
      scope = this + workflowJob,
      runtimeConfig = runtime.runtimeConfig,
      workflowTracer = SystemTracer,
      onOutput = {}
    )

    val resetStateAction = action<Any?, Int, Nothing>("resetState") {
      this.state = 0
    }

    benchmarkRule.measureRepeated {
      runWithTimingDisabled {
        // Clear the workflow tree.
        actionSinks.forEachIndexed { index, sink ->
          sink!!.send(resetStateAction)
        }
        testScheduler.advanceUntilIdle()
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
      testScheduler.advanceUntilIdle()
      assertEquals(expectedTestRendering, renderings.value.rendering)
    }

    workflowJob.cancel()
  }
}

private object SystemTracer : WorkflowTracer {
  override fun beginSection(label: String) = Trace.beginSection(label)
  override fun endSection() = Trace.endSection()
}

private class BenchmarkWorkflowRoot(
  private val treeShape: BenchmarkTreeShape,
  private val actionSinks: Array<Sink<WorkflowAction<*, Int, Nothing>>?>? = null,
) : StatelessWorkflow<BenchmarkWorkflowRoot.Props, Nothing, Int>() {
  data class Props(
    val renderFirstLeaf: Boolean = true,
    val renderOtherLeaves: Boolean = true,
    val firstLeafProps: Int = 0,
    val otherLeafProps: Int = 0,
  )

  private val intermediateChild = IntermediateChild()
  private val leaf = Leaf()

  override fun render(
    renderProps: Props,
    context: RenderContext<Props, Nothing>
  ): Int {
    return context.renderChild(
      child = intermediateChild,
      props = IntermediateProps(
        firstLeafIndex = 0,
        depth = 0,
        renderFirstLeaf = renderProps.renderFirstLeaf,
        renderOtherLeaves = renderProps.renderOtherLeaves,
        firstLeafProps = renderProps.firstLeafProps,
        otherLeafProps = renderProps.otherLeafProps,
      )
    )
  }

  private data class IntermediateProps(
    val firstLeafIndex: Int,
    val depth: Int,
    val renderFirstLeaf: Boolean,
    val renderOtherLeaves: Boolean,
    val firstLeafProps: Int,
    val otherLeafProps: Int,
  )

  private inner class IntermediateChild : StatelessWorkflow<IntermediateProps, Nothing, Int>() {
    override fun render(
      renderProps: IntermediateProps,
      context: RenderContext<IntermediateProps, Nothing>
    ): Int {
      val renderingLeaves = renderProps.depth == treeShape.depth - 1
      val subtreeDepth = treeShape.depth - renderProps.depth - 1
      val subtreeLeafCount = leafCount(degree = treeShape.degree, depth = subtreeDepth)
      var rendering = 0

      // Do some extra work to more closely emulate real-world workflows. This helps the benchmarks
      // show the benefit of avoiding render passes more than just purely rendering children.
      context.runningSideEffect("sideEffect") {
        awaitCancellation()
      }
      context.remember("leafCount", subtreeLeafCount) { subtreeLeafCount }
      context.remember("firstLeaf", renderProps.firstLeafIndex) { renderProps.firstLeafIndex }

      repeat(treeShape.degree) { childIndex ->
        val firstLeafIndex = renderProps.firstLeafIndex + (childIndex * subtreeLeafCount)
        rendering += if (renderingLeaves) {
          if ((renderProps.renderFirstLeaf && firstLeafIndex == 0) ||
            (renderProps.renderOtherLeaves && firstLeafIndex != 0)
          ) {
            val leafRendering = context.renderChild(
              child = leaf,
              key = childIndex.toString(),
              props = LeafProps(
                index = firstLeafIndex,
                value =
                  if (firstLeafIndex == 0) renderProps.firstLeafProps
                  else renderProps.otherLeafProps
              )
            )
            leafRendering
          } else {
            0
          }
        } else {
          context.renderChild(
            child = intermediateChild,
            key = childIndex.toString(),
            props = renderProps.copy(
              firstLeafIndex = firstLeafIndex,
              depth = renderProps.depth + 1,
            )
          )
        }
      }

      return rendering
    }
  }

  private data class LeafProps(
    val index: Int,
    val value: Int,
  )

  private inner class Leaf : StatefulWorkflow<LeafProps, Int, Nothing, Int>() {
    override fun initialState(
      props: LeafProps,
      snapshot: Snapshot?
    ): Int = 0

    override fun render(
      renderProps: LeafProps,
      renderState: Int,
      context: RenderContext<LeafProps, Int, Nothing>
    ): Int {
      // Do some extra work to more closely emulate real-world workflows. This helps the benchmarks
      // show the benefit of avoiding render passes more than just purely rendering children.
      context.runningSideEffect("sideEffect") {
        awaitCancellation()
      }
      context.remember("initialValue") { renderProps.value }
      context.remember("initialState") { renderState }

      if (actionSinks != null) {
        @Suppress("UNCHECKED_CAST")
        actionSinks[renderProps.index] = context.actionSink as Sink<WorkflowAction<*, Int, Nothing>>
      }
      return renderProps.value + renderState
    }

    override fun snapshotState(state: Int): Snapshot? = null
  }
}

private val BenchmarkTreeShape.leafCount: Int get() = leafCount(degree = degree, depth = depth)

private fun leafCount(
  degree: Int,
  depth: Int
): Int = degree.toFloat().pow(depth).toInt()

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
