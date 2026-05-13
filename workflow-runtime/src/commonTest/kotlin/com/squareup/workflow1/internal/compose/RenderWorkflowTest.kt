package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.mutableStateOf
import app.cash.burst.Burst
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.RuntimeConfigOptions.Companion.RuntimeOptions
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.StatelessWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.action
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.stateless
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(WorkflowExperimentalRuntime::class)
enum class TestConfig(val runtimeOptions: RuntimeOptions) {
  COMPOSE_NON_SKIPPING(RuntimeOptions.COMPOSE_RUNTIME_NON_SKIPPING),
  COMPOSE_SKIPPING(RuntimeOptions.COMPOSE_RUNTIME_SKIPPING),
}

@Burst
@OptIn(WorkflowExperimentalRuntime::class)
internal class RenderWorkflowTest(
  val config: TestConfig = TestConfig.COMPOSE_SKIPPING
) {

  @BeforeTest fun setUp() {
    enableImmediateApplyForTests()
  }

  private val skippingConfig = WorkflowComposableRuntimeConfig(
    runtimeConfig = config.runtimeOptions.runtimeConfig,
    idCounter = IdCounter(),
  )

  @Test fun skips_render_when_props_and_onOutput_unchanged() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      var renderCount = 0
      val workflow = Workflow.stateless<Int, Nothing, Int> { props ->
        renderCount++
        props * 2
      }
      val onOutput: (Nothing) -> Unit = {}
      // Force the surrounding composable to recompose between calls without changing any of
      // renderWorkflow's parameters. This is what exercises the skipping path: the restart group
      // around renderWorkflow is reachable on recomposition, but its dirty bits show all params
      // are unchanged, so the inner producer should be skipped.
      val tick = mutableStateOf(0)
      val content: @Composable () -> Int = {
        tick.value
        renderWorkflow(
          workflow = workflow,
          props = 5,
          onOutput = onOutput,
          config = skippingConfig,
          parentSession = null,
          renderKey = "",
        )
      }

      var expectedRenderCount = 1
      assertEquals(10, test.recompose(content))
      assertEquals(expectedRenderCount, renderCount)

      tick.value = 1
      if (RuntimeConfigOptions.COMPOSE_RUNTIME_SKIPPING !in config.runtimeOptions.runtimeConfig) {
        expectedRenderCount++
      }
      assertEquals(10, test.recompose(content))
      assertEquals(expectedRenderCount, renderCount)

      tick.value = 2
      if (RuntimeConfigOptions.COMPOSE_RUNTIME_SKIPPING !in config.runtimeOptions.runtimeConfig) {
        expectedRenderCount++
      }
      assertEquals(10, test.recompose(content))
      assertEquals(expectedRenderCount, renderCount)
    } finally {
      test.close()
    }
  }

  @Test fun rerenders_when_props_change() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      var renderCount = 0
      val workflow = Workflow.stateless<Int, Nothing, Int> { props ->
        renderCount++
        props * 2
      }
      val onOutput: (Nothing) -> Unit = {}
      val propsState = mutableStateOf(5)
      val content: @Composable () -> Int = {
        renderWorkflow(
          workflow = workflow,
          props = propsState.value,
          onOutput = onOutput,
          config = skippingConfig,
          parentSession = null,
          renderKey = "",
        )
      }

      assertEquals(10, test.recompose(content))
      assertEquals(1, renderCount)

      propsState.value = 7
      assertEquals(14, test.recompose(content))
      assertEquals(2, renderCount)

      propsState.value = 11
      assertEquals(22, test.recompose(content))
      assertEquals(3, renderCount)
    } finally {
      test.close()
    }
  }

  @Test fun rerenders_when_onOutput_changes() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      var renderCount = 0
      val workflow = Workflow.stateless<Int, Nothing, Int> { props ->
        renderCount++
        props * 2
      }
      val onOutputState = mutableStateOf<((Nothing) -> Unit)?>(null)
      val content: @Composable () -> Int = {
        renderWorkflow(
          workflow = workflow,
          props = 5,
          onOutput = onOutputState.value,
          config = skippingConfig,
          parentSession = null,
          renderKey = "",
        )
      }

      assertEquals(10, test.recompose(content))
      assertEquals(1, renderCount)

      onOutputState.value = {}
      assertEquals(10, test.recompose(content))
      assertEquals(2, renderCount)

      onOutputState.value = {}
      assertEquals(10, test.recompose(content))
      assertEquals(3, renderCount)
    } finally {
      test.close()
    }
  }

  @Test fun rerenders_when_internal_state_changes() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      var renderCount = 0
      var capturedSink: Sink<WorkflowAction<Int, Int, Nothing>>? = null
      val workflow = object : StatefulWorkflow<Int, Int, Nothing, Int>() {
        override fun initialState(
          props: Int,
          snapshot: Snapshot?
        ): Int = 0

        override fun render(
          renderProps: Int,
          renderState: Int,
          context: RenderContext<Int, Int, Nothing>,
        ): Int {
          renderCount++
          capturedSink = context.actionSink
          return renderProps + renderState
        }

        override fun snapshotState(state: Int): Snapshot? = null
      }
      val onOutput: (Nothing) -> Unit = {}
      val content: @Composable () -> Int = {
        renderWorkflow(
          workflow = workflow,
          props = 5,
          onOutput = onOutput,
          config = skippingConfig,
          parentSession = null,
          renderKey = "",
        )
      }

      assertEquals(5, test.recompose(content))
      assertEquals(1, renderCount)
      val sink = checkNotNull(capturedSink) { "expected actionSink to be captured during render" }

      // Sending an action that updates state should invalidate renderWorkflow's restart group,
      // forcing a new render even though props/onOutput are unchanged.
      println("OMG test: setting state")
      sink.send(action("setStateTo3") { state = 3 })
      println("OMG test: triggering recompose")
      assertEquals(8, test.recompose(content))
      assertEquals(2, renderCount)

      sink.send(action("setStateTo10") { state = 10 })
      assertEquals(15, test.recompose(content))
      assertEquals(3, renderCount)
    } finally {
      test.close()
    }
  }

  @Test fun rerenders_parent_when_internal_state_changes_rendering() = runTest {
    val test = TestComposition(backgroundScope)
    try {
      var renderCount = 0
      var capturedSink: Sink<WorkflowAction<Int, Int, Nothing>>? = null
      val childWorkflow = object : StatefulWorkflow<Int, Int, Nothing, Int>() {
        override fun initialState(
          props: Int,
          snapshot: Snapshot?
        ): Int = 0

        override fun render(
          renderProps: Int,
          renderState: Int,
          context: RenderContext<Int, Int, Nothing>,
        ): Int {
          println("OMG test: recomposing child")
          capturedSink = context.actionSink
          return (renderProps + renderState).also { println("OMG test: new child rendering: $it") }
        }

        override fun snapshotState(state: Int): Snapshot? = null
      }
      val parentWorkflow = object : StatefulWorkflow<Int, Unit, Nothing, Int>() {
        override fun initialState(
          props: Int,
          snapshot: Snapshot?
        ): Unit = Unit

        override fun render(
          renderProps: Int,
          renderState: Unit,
          context: RenderContext<Int, Unit, Nothing>,
        ): Int {
          println("OMG test: recomposing parent")
          renderCount++
          val childRendering = context.renderChild(childWorkflow, props = renderProps)
          return childRendering.also { println("OMG test: new parent rendering: $it") }
        }

        override fun snapshotState(state: Unit): Snapshot? = null
      }
      val onOutput: (Nothing) -> Unit = {}
      val content: @Composable () -> Int = {
        withCompositionLocals(LocalRootRecomposeScope provides currentRecomposeScope) {
          println("OMG test: recomposing root (recomposeScope=$currentRecomposeScope)")
          renderWorkflow(
            workflow = parentWorkflow,
            props = 5,
            onOutput = onOutput,
            config = skippingConfig,
            parentSession = null,
            renderKey = "",
          ).also { println("OMG test: new root rendering: $it") }
        }
      }

      assertEquals(5, test.recompose(content))
      assertEquals(1, renderCount)
      val sink = checkNotNull(capturedSink) { "expected actionSink to be captured during render" }

      // Sending an action that updates state should invalidate renderWorkflow's restart group,
      // forcing a new render even though props/onOutput are unchanged.
      println("OMG test: setting state…")
      sink.send(action("setStateTo3") { state = 3 })
      println("OMG test: triggering recompose")
      assertEquals(8, test.recompose(content))
      assertEquals(2, renderCount)

      println("OMG test: setting state again…")
      sink.send(action("setStateTo10") { state = 10 })
      assertEquals(15, test.recompose(content))
      assertEquals(3, renderCount)
    } finally {
      test.close()
    }
  }

  @Test fun skips_siblings_when_child_internal_state_changes() = runTest {
    val isSkipping = RuntimeConfigOptions.COMPOSE_RUNTIME_SKIPPING in config.runtimeOptions.runtimeConfig

    val test = TestComposition(backgroundScope)
    try {
      var renderCount = 0
      var capturedSink: Sink<WorkflowAction<Int, Int, Nothing>>? = null
      val leaf0Workflow = object : StatelessWorkflow<Int, Nothing, Int>() {
        override fun render(
          renderProps: Int,
          context: RenderContext<Int, Nothing>
        ): Int {
          println("OMG test: recomposing leaf0")
          renderCount++
          return renderProps.also { println("OMG test: new leaf0 rendering: $it") }
        }
      }
      val leaf1Workflow = object : StatefulWorkflow<Int, Int, Nothing, Int>() {
        override fun initialState(
          props: Int,
          snapshot: Snapshot?
        ): Int = 0

        override fun render(
          renderProps: Int,
          renderState: Int,
          context: RenderContext<Int, Int, Nothing>,
        ): Int {
          println("OMG test: recomposing leaf1")
          renderCount++
          capturedSink = context.actionSink
          return (renderProps + renderState).also { println("OMG test: new leaf1 rendering: $it") }
        }

        override fun snapshotState(state: Int): Snapshot? = null
      }
      val childWorkflow = object : StatelessWorkflow<Int, Nothing, Int>() {
        override fun render(
          renderProps: Int,
          context: RenderContext<Int, Nothing>
        ): Int {
          println("OMG test: recomposing child")
          renderCount++
          val leaf0Rendering =
            context.renderChild(leaf0Workflow, key = "leaf0", props = renderProps)
          val leaf1Rendering =
            context.renderChild(leaf1Workflow, key = "leaf1", props = renderProps)
          val leaf2Rendering =
            context.renderChild(leaf0Workflow, key = "leaf2", props = renderProps)
          return (leaf0Rendering + leaf1Rendering + leaf2Rendering)
            .also { println("OMG test: new child rendering: $it") }
        }
      }
      val parentWorkflow = object : StatefulWorkflow<Int, Unit, Nothing, Int>() {
        override fun initialState(
          props: Int,
          snapshot: Snapshot?
        ): Unit = Unit

        override fun render(
          renderProps: Int,
          renderState: Unit,
          context: RenderContext<Int, Unit, Nothing>,
        ): Int {
          println("OMG test: recomposing parent")
          renderCount++
          val child1Rendering =
            context.renderChild(childWorkflow, key = "child1", props = renderProps)
          val child2Rendering =
            context.renderChild(childWorkflow, key = "child2", props = renderProps)
          return (child1Rendering + child2Rendering).also { println("OMG test: new parent rendering: $it") }
        }

        override fun snapshotState(state: Unit): Snapshot? = null
      }
      val onOutput: (Nothing) -> Unit = {}
      val content: @Composable () -> Int = {
        withCompositionLocals(LocalRootRecomposeScope provides currentRecomposeScope) {
          println("OMG test: recomposing root (recomposeScope=$currentRecomposeScope)")
          renderWorkflow(
            workflow = parentWorkflow,
            props = 5,
            onOutput = onOutput,
            config = skippingConfig,
            parentSession = null,
            renderKey = "",
          ).also { println("OMG test: new root rendering: $it") }
        }
      }

      assertEquals(30, test.recompose(content))
      var expectedRenderCount = 9
      assertEquals(expectedRenderCount, renderCount)
      val sink = checkNotNull(capturedSink) { "expected actionSink to be captured during render" }

      // Sending an action that updates state should invalidate renderWorkflow's restart group,
      // forcing a new render even though props/onOutput are unchanged.
      println("OMG test: setting state…")
      sink.send(action("setStateTo3") { state = 3 })
      println("OMG test: triggering recompose")
      assertEquals(33, test.recompose(content))
      expectedRenderCount += if (isSkipping) 3 else 9
      assertEquals(expectedRenderCount, renderCount)

      println("OMG test: setting state again…")
      sink.send(action("setStateTo10") { state = 10 })
      assertEquals(40, test.recompose(content))
      expectedRenderCount += if (isSkipping) 3 else 9
      assertEquals(expectedRenderCount, renderCount)
    } finally {
      test.close()
    }
  }
}
