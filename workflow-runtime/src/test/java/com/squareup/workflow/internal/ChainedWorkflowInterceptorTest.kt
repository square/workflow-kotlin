/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("UNCHECKED_CAST")

package com.squareup.workflow.internal

import com.squareup.workflow.ExperimentalWorkflowApi
import com.squareup.workflow.RenderContext
import com.squareup.workflow.Sink
import com.squareup.workflow.Snapshot
import com.squareup.workflow.Worker
import com.squareup.workflow.Workflow
import com.squareup.workflow.WorkflowAction
import com.squareup.workflow.WorkflowIdentifier
import com.squareup.workflow.WorkflowInterceptor
import com.squareup.workflow.NoopWorkflowInterceptor
import com.squareup.workflow.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow.identifier
import com.squareup.workflow.parse
import com.squareup.workflow.rendering
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
      override fun <P, S, O : Any, R> onRender(
        props: P,
        state: S,
        context: RenderContext<S, O>,
        proceed: (P, S, RenderContext<S, O>) -> R,
        session: WorkflowSession
      ): R = ("r1: " +
          proceed(
              "props1: $props" as P,
              "state1: $state" as S,
              FakeRenderContext("context1: $context") as RenderContext<S, O>
          )) as R
    }
    val interceptor2 = object : WorkflowInterceptor {
      override fun <P, S, O : Any, R> onRender(
        props: P,
        state: S,
        context: RenderContext<S, O>,
        proceed: (P, S, RenderContext<S, O>) -> R,
        session: WorkflowSession
      ): R = ("r2: " +
          proceed(
              "props2: $props" as P,
              "state2: $state" as S,
              FakeRenderContext("context2: $context") as RenderContext<S, O>
          )) as R
    }
    val chained = listOf(interceptor1, interceptor2).chained()
    fun render(
      props: String,
      state: String,
      context: RenderContext<String, String>
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
        proceed: (S) -> Snapshot,
        session: WorkflowSession
      ): Snapshot = Snapshot.of("r1: " + proceed("state1: $state" as S).readUtf8())
    }
    val interceptor2 = object : WorkflowInterceptor {
      override fun <S> onSnapshotState(
        state: S,
        proceed: (S) -> Snapshot,
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

  private class FakeRenderContext(private val name: String) : RenderContext<String, String> {
    override fun toString(): String = name

    override val actionSink: Sink<WorkflowAction<String, String>>
      get() = fail()

    override fun <EventT : Any> onEvent(
      handler: (EventT) -> WorkflowAction<String, String>
    ): (EventT) -> Unit {
      fail()
    }

    override fun <ChildPropsT, ChildOutputT : Any, ChildRenderingT> renderChild(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      handler: (ChildOutputT) -> WorkflowAction<String, String>
    ): ChildRenderingT {
      fail()
    }

    override fun <T> runningWorker(
      worker: Worker<T>,
      key: String,
      handler: (T) -> WorkflowAction<String, String>
    ) {
      fail()
    }

    override fun runningSideEffect(
      key: String,
      sideEffect: suspend () -> Unit
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
