@file:Suppress("UNCHECKED_CAST")

package com.squareup.workflow1.internal

import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.identifier
import com.squareup.workflow1.parse
import com.squareup.workflow1.rendering
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The chain-ordering tests in this class should use and pass through modified copies of all the
 * parameters and return value to ensure that all values are being threaded through appropriately.
 */
internal class ChainedWorkflowInterceptorTest {
  @Test fun chained_returns_Noop_when_list_is_empty() {
    val list = emptyList<WorkflowInterceptor>()
    val chained = list.chained()
    assertSame(NoopWorkflowInterceptor, chained)
  }

  @Test fun chained_returns_single_element_when_list_size_is_1() {
    val interceptor = object : WorkflowInterceptor {}
    val list = listOf(interceptor)
    val chained = list.chained()
    assertSame(interceptor, chained)
  }

  @Test fun chained_returns_chained_element_when_list_size_is_2() {
    val list = listOf(
      object : WorkflowInterceptor {},
      object : WorkflowInterceptor {}
    )
    val chained = list.chained()
    assertTrue(chained is ChainedWorkflowInterceptor)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun chains_calls_to_onInstanceStarted_in_left_to_right_order() = runTest {
    val events = mutableListOf<String>()
    val interceptor1 = object : WorkflowInterceptor {
      override fun onSessionStarted(
        workflowScope: CoroutineScope,
        session: WorkflowSession
      ) {
        events += "started1"
        workflowScope.coroutineContext[Job]!!.invokeOnCompletion {
          events += "cancelled1"
        }
      }
    }
    val interceptor2 = object : WorkflowInterceptor {
      override fun onSessionStarted(
        workflowScope: CoroutineScope,
        session: WorkflowSession
      ) {
        events += "started2"
        workflowScope.coroutineContext[Job]!!.invokeOnCompletion {
          events += "cancelled2"
        }
      }
    }
    val chained = listOf(interceptor1, interceptor2).chained()

    launch {
      chained.onSessionStarted(this, TestSession)
    }
    advanceUntilIdle()

    assertEquals(listOf("started1", "started2", "cancelled1", "cancelled2"), events)
  }

  @Test fun chains_calls_to_onInitialState_in_left_to_right_order() {
    val interceptor1 = object : WorkflowInterceptor {
      override fun <P, S> onInitialState(
        props: P,
        snapshot: Snapshot?,
        workflowScope: CoroutineScope,
        proceed: (P, Snapshot?, CoroutineScope) -> S,
        session: WorkflowSession
      ): S = (
        "r1: " +
          proceed(
            "props1: $props" as P,
            Snapshot.of("snap1: ${snapshot.readUtf8()}"),
            workflowScope
          )
        ) as S
    }
    val interceptor2 = object : WorkflowInterceptor {
      override fun <P, S> onInitialState(
        props: P,
        snapshot: Snapshot?,
        workflowScope: CoroutineScope,
        proceed: (P, Snapshot?, CoroutineScope) -> S,
        session: WorkflowSession
      ): S = (
        "r2: " +
          proceed(
            "props2: $props" as P,
            Snapshot.of("snap2: ${snapshot.readUtf8()}"),
            workflowScope
          )
        ) as S
    }
    val chained = listOf(interceptor1, interceptor2).chained()

    @Suppress("UNUSED_PARAMETER")
    fun initialState(
      props: String,
      snapshot: Snapshot?,
      workflowScope: CoroutineScope
    ): String = "($props|${snapshot.readUtf8()})"

    val finalState =
      chained.onInitialState(
        "props",
        Snapshot.of("snap"),
        CoroutineScope(EmptyCoroutineContext),
        ::initialState,
        TestSession
      )

    assertEquals("r1: r2: (props2: props1: props|snap2: snap1: snap)", finalState)
  }

  @Test fun chains_calls_to_onPropsChanged_in_left_to_right_order() {
    val interceptor1 = object : WorkflowInterceptor {
      override fun <P, S> onPropsChanged(
        old: P,
        new: P,
        state: S,
        proceed: (P, P, S) -> S,
        session: WorkflowSession
      ): S = (
        "s1: " +
          proceed(
            "old1: $old" as P,
            "new1: $new" as P,
            "state1: $state" as S
          )
        ) as S
    }
    val interceptor2 = object : WorkflowInterceptor {
      override fun <P, S> onPropsChanged(
        old: P,
        new: P,
        state: S,
        proceed: (P, P, S) -> S,
        session: WorkflowSession
      ): S = (
        "s2: " +
          proceed(
            "old2: $old" as P,
            "new2: $new" as P,
            "state2: $state" as S
          )
        ) as S
    }
    val chained = listOf(interceptor1, interceptor2).chained()
    fun onPropsChanged(
      old: String,
      new: String,
      state: String
    ): String = "($old|$new|$state)"

    val finalState = chained.onPropsChanged("old", "new", "state", ::onPropsChanged, TestSession)

    assertEquals("s1: s2: (old2: old1: old|new2: new1: new|state2: state1: state)", finalState)
  }

  @Test fun chains_calls_to_onRender_in_left_to_right_order() {
    val interceptor1 = object : WorkflowInterceptor {
      override fun <P, S, O, R> onRender(
        renderProps: P,
        renderState: S,
        context: BaseRenderContext<P, S, O>,
        proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
        session: WorkflowSession
      ) = "r1: ${proceed("props1: $renderProps" as P, "state1: $renderState" as S, null)}" as R
    }
    val interceptor2 = object : WorkflowInterceptor {
      override fun <P, S, O, R> onRender(
        renderProps: P,
        renderState: S,
        context: BaseRenderContext<P, S, O>,
        proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
        session: WorkflowSession
      ) = "r2: ${proceed("props2: $renderProps" as P, "state2: $renderState" as S, null)}" as R
    }
    val chained = listOf(interceptor1, interceptor2).chained()

    val finalRendering =
      chained.onRender<String, String, Nothing, Any>(
        "props",
        "state",
        FakeRenderContext,
        { p, s, _ -> "($p|$s)" },
        TestSession,
      )

    assertEquals(
      "r1: r2: (props2: props1: props|state2: state1: state)",
      finalRendering
    )
  }

  @Test fun chains_calls_with_RenderContextInterceptor_in_left_to_right_order() {
    val transcript = mutableListOf<String>()

    class Labeler<P, S, O>(val label: String) : RenderContextInterceptor<P, S, O> {
      override fun <CP, CO, CR> onRenderChild(
        child: Workflow<CP, CO, CR>,
        childProps: CP,
        key: String,
        handler: (CO) -> WorkflowAction<P, S, O>,
        proceed: (
          child: Workflow<CP, CO, CR>,
          props: CP,
          key: String,
          handler: (CO) -> WorkflowAction<P, S, O>
        ) -> CR
      ): CR {
        transcript += "START $label"
        val r = proceed(child, childProps, key, handler)
        transcript += "END $label"
        return r
      }
    }

    val interceptor1 = object : WorkflowInterceptor {
      override fun <P, S, O, R> onRender(
        renderProps: P,
        renderState: S,
        context: BaseRenderContext<P, S, O>,
        proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
        session: WorkflowSession
      ) = proceed(renderProps, renderState, Labeler("uno"))
    }

    val interceptor2 = object : WorkflowInterceptor {
      override fun <P, S, O, R> onRender(
        renderProps: P,
        renderState: S,
        context: BaseRenderContext<P, S, O>,
        proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
        session: WorkflowSession
      ) = proceed(renderProps, renderState, Labeler("dos"))
    }

    val chained = listOf(interceptor1, interceptor2).chained()

    chained.onRender<String, String, Nothing, Any>(
      renderProps = "props",
      renderState = "state",
      context = FakeRenderContext,
      proceed = { _, _, interceptor ->
        interceptor?.onRenderChild(
          child = TestSession.workflow,
          childProps = Unit,
          key = TestSession.renderKey,
          handler = { error("How did you emit Nothing? Good trick!") },
          proceed = { _, _, _, _ -> }
        ) as Any
      },
      session = TestSession,
    )

    assertEquals("START uno, START dos, END dos, END uno", transcript.joinToString(", "))
  }

  @Test fun chains_calls_to_onSnapshotState_in_left_to_right_order() {
    val interceptor1 = object : WorkflowInterceptor {
      override fun <S> onSnapshotState(
        state: S,
        proceed: (S) -> Snapshot?,
        session: WorkflowSession
      ): Snapshot = Snapshot.of("r1: " + proceed("state1: $state" as S).readUtf8())
    }
    val interceptor2 = object : WorkflowInterceptor {
      override fun <S> onSnapshotState(
        state: S,
        proceed: (S) -> Snapshot?,
        session: WorkflowSession
      ): Snapshot = Snapshot.of("r2: " + proceed("state2: $state" as S).readUtf8())
    }
    val chained = listOf(interceptor1, interceptor2).chained()
    fun snapshotState(state: String): Snapshot = Snapshot.of("($state)")

    val finalSnapshot = chained.onSnapshotState("state", ::snapshotState, TestSession)
      .readUtf8()

    assertEquals("r1: r2: (state2: state1: state)", finalSnapshot)
  }

  private fun Snapshot?.readUtf8() = this?.bytes?.parse { it.readUtf8() }

  private object FakeRenderContext : BaseRenderContext<String, String, Nothing> {
    override val runtimeConfig: RuntimeConfig = emptySet()
    override val actionSink: Sink<WorkflowAction<String, String, Nothing>>
      get() = fail()
    override val workflowTracer: WorkflowTracer? = null

    override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      handler: (ChildOutputT) -> WorkflowAction<String, String, Nothing>
    ): ChildRenderingT {
      fail()
    }

    override fun runningSideEffect(
      key: String,
      sideEffect: suspend CoroutineScope.() -> Unit
    ) {
      fail()
    }

    override fun <ResultT> remember(
      key: String,
      resultType: KType,
      vararg inputs: Any?,
      calculation: () -> ResultT
    ): ResultT = fail()
  }

  object TestSession : WorkflowSession {
    val workflow = Workflow.rendering<Nothing, Unit>(Unit)

    override val identifier: WorkflowIdentifier = workflow.identifier
    override val renderKey: String = ""
    override val sessionId: Long = 0
    override val parent: WorkflowSession? = null
    override val runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG
    override val workflowTracer: WorkflowTracer? = null
  }
}
