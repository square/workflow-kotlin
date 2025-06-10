@file:Suppress("EXPERIMENTAL_API_USAGE", "OverridingDeprecatedMember")

package com.squareup.workflow1.internal

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.internal.RealRenderContext.RememberStore
import com.squareup.workflow1.internal.RealRenderContext.Renderer
import com.squareup.workflow1.internal.RealRenderContext.SideEffectRunner
import com.squareup.workflow1.internal.RealRenderContextTest.TestRenderer.Rendering
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.stateless
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

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

  private class TestRememberStore : RememberStore {
    override fun <ResultT> remember(
      key: String,
      resultType: KType,
      vararg inputs: Any?,
      calculation: () -> ResultT
    ): ResultT {
      return calculation()
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
      context: StatefulWorkflow.RenderContext<String, String, String>
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

  @Test fun remember_passes_through_to_remember_store() {
    val context = createTestContext()

    assertEquals(
      "value",
      context.remember("key", typeOf<String>()) { "value" }
    )
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
    assertFailsWith<IllegalStateException> { context.remember("key", typeOf<String>()) {} }
  }

  private fun createdPoisonedContext(): RealRenderContext<String, String, String> {
    val sideEffectRunner = PoisonRunner()
    val rememberStore = TestRememberStore()
    return RealRenderContext(
      PoisonRenderer(),
      sideEffectRunner,
      rememberStore,
      eventActionsChannel,
      workflowTracer = null,
      runtimeConfig = emptySet(),
    )
  }

  private fun createTestContext(): RealRenderContext<String, String, String> {
    val sideEffectRunner = TestRunner()
    val rememberStore = TestRememberStore()
    return RealRenderContext(
      TestRenderer(),
      sideEffectRunner,
      rememberStore,
      eventActionsChannel,
      workflowTracer = null,
      runtimeConfig = emptySet(),
    )
  }
}
