@file:Suppress("EXPERIMENTAL_API_USAGE", "OverridingDeprecatedMember")

package com.squareup.workflow1.internal

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.internal.RealRenderContext.Renderer
import com.squareup.workflow1.internal.RealRenderContext.SideEffectRunner
import com.squareup.workflow1.internal.RealRenderContextTest.TestRenderer.Rendering
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.stateless
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Cheap hack to avoid lines that are too long and cause auto-format v. lint pain.
 */
private typealias S = String

internal class RealRenderContextTest {

  private class TestRenderer : Renderer<String, String, String> {

    data class Rendering(
      val child: Workflow<*, *, *>,
      val props: Any?,
      val key: String,
      val handler: (Any) -> WorkflowAction<String, String, String>
    )

    @Suppress("UNCHECKED_CAST")
    override fun <ChildPropsT, ChildOutputT, ChildRenderingT> render(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      handler: (ChildOutputT) -> WorkflowAction<String, String, String>
    ): ChildRenderingT = Rendering(
      child,
      props,
      key,
      handler as (Any) -> WorkflowAction<String, String, String>
    ) as ChildRenderingT
  }

  private class TestRunner : SideEffectRunner {
    override fun runningSideEffect(
      key: String,
      sideEffect: suspend CoroutineScope.() -> Unit
    ) {
      // No-op
    }
  }

  private class TestWorkflow : StatefulWorkflow<String, String, String, Rendering>() {
    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ): String = fail()

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext
    ): Rendering {
      fail("This shouldn't actually be called.")
    }

    override fun snapshotState(state: String): Snapshot = fail()
  }

  private class PoisonRenderer<P, S, O : Any> : Renderer<P, S, O> {
    override fun <ChildPropsT, ChildOutputT, ChildRenderingT> render(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      handler: (ChildOutputT) -> WorkflowAction<P, S, O>
    ): ChildRenderingT = fail()
  }

  private class PoisonRunner : SideEffectRunner {
    override fun runningSideEffect(
      key: String,
      sideEffect: suspend CoroutineScope.() -> Unit
    ) {
      fail()
    }
  }

  private val eventActionsChannel =
    Channel<WorkflowAction<String, String, String>>(capacity = UNLIMITED)

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun send_completes_update() {
    val context = createdPoisonedContext()
    val stringAction = action<String, String, String>({ "stringAction" }) { }

    // Enable sink sends.
    context.freeze()

    assertTrue(eventActionsChannel.isEmpty)

    context.actionSink.send(stringAction)

    assertFalse(eventActionsChannel.isEmpty)
    val actualAction = eventActionsChannel.tryReceive().getOrNull()
    assertSame(stringAction, actualAction)
  }

  @Test fun send_allows_multiple_sends() {
    val context = createdPoisonedContext()
    val firstAction = object : WorkflowAction<String, String, String>() {
      override val debuggingName: String = "firstAction"
      override fun Updater.apply() = Unit
    }
    val secondAction = object : WorkflowAction<String, String, String>() {
      override val debuggingName: String = "secondAction"
      override fun Updater.apply() = Unit
    }
    // Enable sink sends.
    context.freeze()

    context.actionSink.send(firstAction)

    // Shouldn't throw.
    context.actionSink.send(secondAction)
  }

  @Test fun send_throws_before_render_returns() {
    val context = createdPoisonedContext()
    val action = object : WorkflowAction<String, String, String>() {
      override val debuggingName: String = "action"
      override fun Updater.apply() = Unit
    }

    val error = assertFailsWith<UnsupportedOperationException> {
      context.actionSink.send(action)
    }
    assertEquals(
      "Expected sink to not be sent to until after the render pass. Received action: action",
      error.message
    )
  }

  @Test fun eventHandler0_gets_event() {
    val context = createdPoisonedContext()
    val sink: () -> Unit = context.eventHandler("") { setOutput("yay") }
    // Enable sink sends.
    context.freeze()

    sink()

    val update = eventActionsChannel.tryReceive().getOrNull()!!
    val (state, result) = update.applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("yay", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun eventHandler1_gets_event() {
    val context = createdPoisonedContext()
    val sink = context.eventHandler("") { it: String -> setOutput(it) }
    // Enable sink sends.
    context.freeze()

    sink("foo")

    val update = eventActionsChannel.tryReceive().getOrNull()!!
    val (state, result) = update.applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("foo", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun eventHandler2_gets_event() {
    val context = createdPoisonedContext()
    val sink = context.eventHandler("") { a: String, b: String -> setOutput(a + b) }
    // Enable sink sends.
    context.freeze()

    sink("foo", "bar")

    val update = eventActionsChannel.tryReceive().getOrNull()!!
    val (state, result) = update.applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("foobar", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun eventHandler3_gets_event() {
    val context = createdPoisonedContext()
    val sink = context.eventHandler("") { a: String, b: String, c: String, d: String ->
      setOutput(a + b + c + d)
    }
    // Enable sink sends.
    context.freeze()

    sink("foo", "bar", "baz", "bang")

    val update = eventActionsChannel.tryReceive().getOrNull()!!
    val (state, result) = update.applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("foobarbazbang", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun eventHandler4_gets_event() {
    val context = createdPoisonedContext()
    val sink = context.eventHandler("") { a: String, b: String, c: String, d: String ->
      setOutput(a + b + c + d)
    }
    // Enable sink sends.
    context.freeze()

    sink("foo", "bar", "baz", "bang")

    val update = eventActionsChannel.tryReceive().getOrNull()!!
    val (state, result) = update.applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("foobarbazbang", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun eventHandler5_gets_event() {
    val context = createdPoisonedContext()
    val sink = context.eventHandler("") { a: String, b: String, c: String, d: String, e: String ->
      setOutput(a + b + c + d + e)
    }
    // Enable sink sends.
    context.freeze()

    sink("foo", "bar", "baz", "bang", "buzz")

    val update = eventActionsChannel.tryReceive().getOrNull()!!
    val (state, result) = update.applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("foobarbazbangbuzz", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun eventHandler6_gets_event() {
    val context = createdPoisonedContext()
    val sink =
      context.eventHandler("") { a: String, b: String, c: String, d: String, e: String, f: String ->
        setOutput(a + b + c + d + e + f)
      }
    // Enable sink sends.
    context.freeze()

    sink("foo", "bar", "baz", "bang", "buzz", "qux")

    val update = eventActionsChannel.tryReceive().getOrNull()!!
    val (state, result) = update.applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("foobarbazbangbuzzqux", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun eventHandler7_gets_event() {
    val context = createdPoisonedContext()
    val sink =
      context.eventHandler("") { a: S, b: S, c: S, d: S, e: S, f: S, g: S ->
        setOutput(a + b + c + d + e + f + g)
      }
    // Enable sink sends.
    context.freeze()

    sink("foo", "bar", "baz", "bang", "buzz", "qux", "corge")

    val update = eventActionsChannel.tryReceive().getOrNull()!!
    val (state, result) = update.applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("foobarbazbangbuzzquxcorge", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun eventHandler8_gets_event() {
    val context = createdPoisonedContext()
    val sink =
      context.eventHandler("") { a: S, b: S, c: S, d: S, e: S, f: S, g: S, h: S ->
        setOutput(a + b + c + d + e + f + g + h)
      }
    // Enable sink sends.
    context.freeze()

    sink("foo", "bar", "baz", "bang", "buzz", "qux", "corge", "fred")

    val update = eventActionsChannel.tryReceive().getOrNull()!!
    val (state, result) = update.applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("foobarbazbangbuzzquxcorgefred", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun eventHandler9_gets_event() {
    val context = createdPoisonedContext()
    val sink =
      context.eventHandler("") { a: S, b: S, c: S, d: S, e: S, f: S, g: S, h: S, i: S ->
        setOutput(a + b + c + d + e + f + g + h + i)
      }
    // Enable sink sends.
    context.freeze()

    sink("foo", "bar", "baz", "bang", "buzz", "qux", "corge", "fred", "xyzzy")

    val update = eventActionsChannel.tryReceive().getOrNull()!!
    val (state, result) = update.applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("foobarbazbangbuzzquxcorgefredxyzzy", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun eventHandler10_gets_event() {
    val context = createdPoisonedContext()
    val sink =
      context.eventHandler("") { a: S, b: S, c: S, d: S, e: S, f: S, g: S, h: S, i: S, j: S ->
        setOutput(a + b + c + d + e + f + g + h + i + j)
      }
    // Enable sink sends.
    context.freeze()

    sink("foo", "bar", "baz", "bang", "buzz", "qux", "corge", "fred", "xyzzy", "plugh")

    val update = eventActionsChannel.tryReceive().getOrNull()!!
    val (state, result) = update.applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("foobarbazbangbuzzquxcorgefredxyzzyplugh", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun renderChild_works() {
    val context = createTestContext()
    val workflow = TestWorkflow()

    val (child, props, key, handler) = context.renderChild(workflow, "props", "key") { output ->
      action("") { setOutput("output:$output") }
    }

    assertSame(workflow, child)
    assertEquals("props", props)
    assertEquals("key", key)

    val (state, result) = handler.invoke("output")
      .applyTo("props", "state")
    assertEquals("state", state)
    assertEquals("output:output", result.output!!.value)
    assertFalse(result.stateChanged)
  }

  @Test fun renderChild_handler_tracks_state_change() {
    val context = createTestContext()
    val workflow = TestWorkflow()

    val (child, props, key, handler) = context.renderChild(workflow, "props", "key") {
      action("") {
        state = "new"
      }
    }

    assertSame(workflow, child)
    assertEquals("props", props)
    assertEquals("key", key)

    val (state, result) = handler.invoke("output")
      .applyTo("props", "state")
    assertEquals("new", state)
    assertNull(result.output)
    assertTrue(result.stateChanged)
  }

  @Test fun all_methods_throw_after_freeze() {
    val context = createTestContext()
    context.freeze()

    val child = Workflow.stateless<Unit, Nothing, Unit> { fail() }
    assertFailsWith<IllegalStateException> { context.renderChild(child) }
    assertFailsWith<IllegalStateException> { context.freeze() }
  }

  private fun createdPoisonedContext(): RealRenderContext<String, String, String> {
    val workerRunner = PoisonRunner()
    return RealRenderContext(
      PoisonRenderer(),
      workerRunner,
      eventActionsChannel,
      workflowTracer = null
    )
  }

  private fun createTestContext(): RealRenderContext<String, String, String> {
    val workerRunner = TestRunner()
    return RealRenderContext(
      TestRenderer(),
      workerRunner,
      eventActionsChannel,
      workflowTracer = null
    )
  }
}
