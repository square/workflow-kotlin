@file:Suppress("UNCHECKED_CAST")

package com.squareup.workflow1.internal

import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.identifier
import com.squareup.workflow1.parse
import com.squareup.workflow1.rendering
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The chain-ordering tests in this class should use and pass through modified copies of all the
 * parameters and return value to ensure that all values are being threaded through appropriately.
 */
@OptIn(ExperimentalWorkflowApi::class)
class ChainedWorkflowInterceptorTest {

  @Test fun `chained() returns Noop when list is empty`() {
    val list = emptyList<WorkflowInterceptor>()
    val chained = list.chained()
    assertSame(NoopWorkflowInterceptor, chained)
  }

  @Test fun `chained() returns single element when list size is 1`() {
    val interceptor = object : WorkflowInterceptor {}
    val list = listOf(interceptor)
    val chained = list.chained()
    assertSame(interceptor, chained)
  }

  @Test fun `chained() returns chained element when list size is 2`() {
    val list = listOf(
        object : WorkflowInterceptor {},
        object : WorkflowInterceptor {}
    )
    val chained = list.chained()
    assertTrue(chained is ChainedWorkflowInterceptor)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test fun `chains calls to onInstanceStarted() in left-to-right order`() {
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
    val scope = TestCoroutineScope(Job())

    chained.onSessionStarted(scope, TestSession)
    scope.advanceUntilIdle()
    scope.cancel()

    assertEquals(listOf("started1", "started2", "cancelled1", "cancelled2"), events)
  }

  @Test fun `chains calls to onInitialState() in left-to-right order`() {
    val interceptor1 = object : WorkflowInterceptor {
      override fun <P, S> onInitialState(
        props: P,
        snapshot: Snapshot?,
        proceed: (P, Snapshot?) -> S,
        session: WorkflowSession
      ): S = ("r1: " +
          proceed(
              "props1: $props" as P,
              Snapshot.of("snap1: ${snapshot.readUtf8()}")
          )) as S
    }
    val interceptor2 = object : WorkflowInterceptor {
      override fun <P, S> onInitialState(
        props: P,
        snapshot: Snapshot?,
        proceed: (P, Snapshot?) -> S,
        session: WorkflowSession
      ): S = ("r2: " +
          proceed(
              "props2: $props" as P,
              Snapshot.of("snap2: ${snapshot.readUtf8()}")
          )) as S
    }
    val chained = listOf(interceptor1, interceptor2).chained()
    fun initialState(
      props: String,
      snapshot: Snapshot?
    ): String = "($props|${snapshot.readUtf8()})"

    val finalState =
      chained.onInitialState("props", Snapshot.of("snap"), ::initialState, TestSession)

    assertEquals("r1: r2: (props2: props1: props|snap2: snap1: snap)", finalState)
  }

  @Test fun `chains calls to onPropsChanged() in left-to-right order`() {
    val interceptor1 = object : WorkflowInterceptor {
      override fun <P, S> onPropsChanged(
        old: P,
        new: P,
        state: S,
        proceed: (P, P, S) -> S,
        session: WorkflowSession
      ): S = ("s1: " +
          proceed(
              "old1: $old" as P,
              "new1: $new" as P,
              "state1: $state" as S
          )) as S
    }
    val interceptor2 = object : WorkflowInterceptor {
      override fun <P, S> onPropsChanged(
        old: P,
        new: P,
        state: S,
        proceed: (P, P, S) -> S,
        session: WorkflowSession
      ): S = ("s2: " +
          proceed(
              "old2: $old" as P,
              "new2: $new" as P,
              "state2: $state" as S
          )) as S
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

  @Test fun `chains calls to onRender() in left-to-right order`() {
    val interceptor1 = object : WorkflowInterceptor {
      override fun <P, S, O, R> onRender(
        props: P,
        state: S,
        context: BaseRenderContext<P, S, O>,
        proceed: (P, S, BaseRenderContext<P, S, O>) -> R,
        session: WorkflowSession
      ): R = ("r1: " +
          proceed(
              "props1: $props" as P,
              "state1: $state" as S,
              FakeRenderContext("context1: $context") as BaseRenderContext<P, S, O>
          )) as R
    }
    val interceptor2 = object : WorkflowInterceptor {
      override fun <P, S, O, R> onRender(
        props: P,
        state: S,
        context: BaseRenderContext<P, S, O>,
        proceed: (P, S, BaseRenderContext<P, S, O>) -> R,
        session: WorkflowSession
      ): R = ("r2: " +
          proceed(
              "props2: $props" as P,
              "state2: $state" as S,
              FakeRenderContext("context2: $context") as BaseRenderContext<P, S, O>
          )) as R
    }
    val chained = listOf(interceptor1, interceptor2).chained()
    fun render(
      props: String,
      state: String,
      context: BaseRenderContext<String, String, String>
    ): String = "($props|$state|$context)"

    val finalRendering =
      chained.onRender("props", "state", FakeRenderContext("context"), ::render, TestSession)

    assertEquals(
        "r1: r2: (props2: props1: props|state2: state1: state|context2: context1: context)",
        finalRendering
    )
  }

  @Test fun `chains calls to onSnapshotState() in left-to-right order`() {
    val interceptor1 = object : WorkflowInterceptor {
      override fun <S> onSnapshotState(
        state: S,
        proceed: (S) -> Snapshot?,
        session: WorkflowSession
      ): Snapshot? = Snapshot.of("r1: " + proceed("state1: $state" as S).readUtf8())
    }
    val interceptor2 = object : WorkflowInterceptor {
      override fun <S> onSnapshotState(
        state: S,
        proceed: (S) -> Snapshot?,
        session: WorkflowSession
      ): Snapshot? = Snapshot.of("r2: " + proceed("state2: $state" as S).readUtf8())
    }
    val chained = listOf(interceptor1, interceptor2).chained()
    fun snapshotState(state: String): Snapshot = Snapshot.of("($state)")

    val finalSnapshot = chained.onSnapshotState("state", ::snapshotState, TestSession)
        .readUtf8()

    assertEquals("r1: r2: (state2: state1: state)", finalSnapshot)
  }

  private fun Snapshot?.readUtf8() = this?.bytes?.parse { it.readUtf8() }

  private class FakeRenderContext(
    private val name: String
  ) : BaseRenderContext<String, String, String> {
    override fun toString(): String = name

    override val actionSink: Sink<WorkflowAction<String, String, String>>
      get() = fail()

    override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      handler: (ChildOutputT) -> WorkflowAction<String, String, String>
    ): ChildRenderingT {
      fail()
    }

    override fun runningSideEffect(
      key: String,
      sideEffect: suspend CoroutineScope.() -> Unit
    ) {
      fail()
    }
  }

  object TestSession : WorkflowSession {
    override val identifier: WorkflowIdentifier = Workflow.rendering(Unit).identifier
    override val renderKey: String = ""
    override val sessionId: Long = 0
    override val parent: WorkflowSession? = null
  }
}
