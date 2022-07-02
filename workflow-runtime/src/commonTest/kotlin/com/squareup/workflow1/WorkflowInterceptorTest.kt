@file:Suppress("OverridingDeprecatedMember")

package com.squareup.workflow1

import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.CoroutineContext.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkflowInterceptorTest {

  @Test fun intercept_returns_workflow_when_Noop() {
    val interceptor = NoopWorkflowInterceptor
    val workflow = Workflow.rendering("hello")
      .asStatefulWorkflow()
    val intercepted = interceptor.intercept(workflow, workflow.session)
    assertSame(workflow, intercepted)
  }

  @Test fun intercept_intercepts_calls_to_initialState() {
    val recorder = RecordingWorkflowInterceptor()
    val intercepted = recorder.intercept(TestWorkflow, TestWorkflow.session)

    val state = intercepted.initialState("props", Snapshot.of("snapshot"))

    assertEquals("props|snapshot", state)
    assertEquals(listOf("BEGIN|onInitialState", "END|onInitialState"), recorder.consumeEventNames())
  }

  @Test fun intercept_intercepts_calls_to_onPropsChanged() {
    val recorder = RecordingWorkflowInterceptor()
    val intercepted = recorder.intercept(TestWorkflow, TestWorkflow.session)

    val state = intercepted.onPropsChanged("old", "new", "state")

    assertEquals("old|new|state", state)
    assertEquals(listOf("BEGIN|onPropsChanged", "END|onPropsChanged"), recorder.consumeEventNames())
  }

  @Test fun intercept_intercepts_calls_to_render() {
    val recorder = RecordingWorkflowInterceptor()
    val intercepted = recorder.intercept(TestWorkflow, TestWorkflow.session)
    val fakeContext = object : BaseRenderContext<String, String, String> {
      override val actionSink: Sink<WorkflowAction<String, String, String>> get() = fail()

      override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
        child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
        props: ChildPropsT,
        key: String,
        handler: (ChildOutputT) -> WorkflowAction<String, String, String>
      ): ChildRenderingT = fail()

      override fun runningSideEffect(
        key: String,
        sideEffect: suspend CoroutineScope.() -> Unit
      ) = fail()
    }

    val rendering = intercepted.render("props", "state", RenderContext(fakeContext, TestWorkflow))

    assertEquals("props|state", rendering)
    assertEquals(listOf("BEGIN|onRender", "END|onRender"), recorder.consumeEventNames())
  }

  @Test fun intercept_intercepts_calls_to_snapshotState() {
    val recorder = RecordingWorkflowInterceptor()
    val intercepted = recorder.intercept(TestWorkflow, TestWorkflow.session)

    val snapshot = intercepted.snapshotState("state")

    assertEquals(Snapshot.of("state"), snapshot)
    assertEquals(
      listOf("BEGIN|onSnapshotState", "END|onSnapshotState"), recorder.consumeEventNames()
    )
  }

  @Test fun intercept_intercepts_calls_to_actionSink_send() {
    val recorder = RecordingWorkflowInterceptor()
    val intercepted = recorder.intercept(TestActionWorkflow, TestActionWorkflow.session)
    val actions = mutableListOf<WorkflowAction<String, String, String>>()

    val fakeContext = object : BaseRenderContext<String, String, String> {
      override val actionSink: Sink<WorkflowAction<String, String, String>> =
        Sink { value -> actions += value }

      override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
        child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
        props: ChildPropsT,
        key: String,
        handler: (ChildOutputT) -> WorkflowAction<String, String, String>
      ): ChildRenderingT = fail()

      override fun runningSideEffect(
        key: String,
        sideEffect: suspend CoroutineScope.() -> Unit
      ) = fail()
    }

    val rendering =
      intercepted.render("props", "string", RenderContext(fakeContext, TestActionWorkflow))

    assertTrue(actions.isEmpty())
    rendering.onEvent()
    assertTrue(actions.size == 1)

    assertEquals(
      listOf("BEGIN|onRender", "END|onRender", "BEGIN|onActionSent", "END|onActionSent"),
      recorder.consumeEventNames()
    )
  }

  @Test fun intercept_intercepts_side_effects() {
    val recorder = RecordingWorkflowInterceptor()
    val workflow = TestSideEffectWorkflow()
    val intercepted = recorder.intercept(workflow, workflow.session)
    val fakeContext = object : BaseRenderContext<String, String, String> {
      override val actionSink: Sink<WorkflowAction<String, String, String>> get() = fail()

      override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
        child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
        props: ChildPropsT,
        key: String,
        handler: (ChildOutputT) -> WorkflowAction<String, String, String>
      ): ChildRenderingT = fail()

      override fun runningSideEffect(
        key: String,
        sideEffect: suspend CoroutineScope.() -> Unit
      ) {
        runBlocking { sideEffect() }
      }
    }

    intercepted.render("props", "string", RenderContext(fakeContext, workflow))

    assertEquals(
      listOf(
        "BEGIN|onRender",
        "BEGIN|onSideEffectRunning",
        "END|onSideEffectRunning",
        "END|onRender"
      ),
      recorder.consumeEventNames()
    )
  }

  @Test fun intercept_uses_interceptors_context_for_side_effect() {
    val recorder = object : RecordingWorkflowInterceptor() {
      override fun <P, S, O, R> onRender(
        renderProps: P,
        renderState: S,
        context: BaseRenderContext<P, S, O>,
        proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
        session: WorkflowSession
      ): R {
        return proceed(
          renderProps, renderState,
          object : RenderContextInterceptor<P, S, O> {
            override fun onRunningSideEffect(
              key: String,
              sideEffect: suspend () -> Unit,
              proceed: (key: String, sideEffect: suspend () -> Unit) -> Unit
            ) {
              TestScope(TestElement).launch {
                proceed(key, sideEffect)
              }
            }
          }
        )
      }
    }

    val workflow = TestSideEffectWorkflow(expectContextElementInSideEffect = true)
    val intercepted = recorder.intercept(workflow, workflow.session)
    val fakeContext = object : BaseRenderContext<String, String, String> {
      override val actionSink: Sink<WorkflowAction<String, String, String>> get() = fail()

      override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
        child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
        props: ChildPropsT,
        key: String,
        handler: (ChildOutputT) -> WorkflowAction<String, String, String>
      ): ChildRenderingT = fail()

      override fun runningSideEffect(
        key: String,
        sideEffect: suspend CoroutineScope.() -> Unit
      ) {
        runBlocking { sideEffect() }
      }
    }

    intercepted.render("props", "string", RenderContext(fakeContext, workflow))
  }

  private val Workflow<*, *, *>.session: WorkflowSession
    get() = object : WorkflowSession {
      override val identifier: WorkflowIdentifier = this@session.identifier
      override val renderKey: String = ""
      override val sessionId: Long = 0
      override val parent: WorkflowSession? = null
    }

  private object TestWorkflow : StatefulWorkflow<String, String, String, String>() {
    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ): String = "$props|${snapshot?.bytes?.parse { it.readUtf8() }}"

    override fun onPropsChanged(
      old: String,
      new: String,
      state: String
    ): String = "$old|$new|$state"

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext
    ): String = "$renderProps|$renderState"

    override fun snapshotState(state: String): Snapshot = Snapshot.of(state)
  }

  private class TestRendering(val onEvent: () -> Unit)
  private object TestActionWorkflow : StatefulWorkflow<String, String, String, TestRendering>() {
    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ) = ""

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext
    ): TestRendering {
      return TestRendering(context.eventHandler { state = "$state: fired" })
    }

    override fun snapshotState(state: String): Snapshot? = null
  }

  private object TestElement : CoroutineContext.Element {
    override val key = object : Key<TestElement> {}
  }

  private class TestSideEffectWorkflow(
    val expectContextElementInSideEffect: Boolean = false
  ) : StatefulWorkflow<String, String, String, String>() {
    override fun initialState(
      props: String,
      snapshot: Snapshot?
    ) = ""

    override fun render(
      renderProps: String,
      renderState: String,
      context: RenderContext
    ): String {
      context.runningSideEffect("sideEffectKey") {
        if (expectContextElementInSideEffect) assertNotNull(coroutineContext[TestElement.key])
      }
      return ""
    }

    override fun snapshotState(state: String): Snapshot? = null
  }
}
