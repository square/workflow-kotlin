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
@file:Suppress("OverridingDeprecatedMember")

package com.squareup.workflow1

import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.fail

@OptIn(ExperimentalWorkflowApi::class)
class WorkflowInterceptorTest {

  @Test fun `intercept() returns workflow when Noop`() {
    val interceptor = NoopWorkflowInterceptor
    val workflow = Workflow.rendering("hello")
        .asStatefulWorkflow()
    val intercepted = interceptor.intercept(workflow, workflow.session)
    assertSame(workflow, intercepted)
  }

  @Test fun `intercept() intercepts calls to initialState()`() {
    val recorder = RecordingWorkflowInterceptor()
    val intercepted = recorder.intercept(TestWorkflow, TestWorkflow.session)

    val state = intercepted.initialState("props", Snapshot.of("snapshot"))

    assertEquals("props|snapshot", state)
    assertEquals(listOf("BEGIN|onInitialState", "END|onInitialState"), recorder.consumeEventNames())
  }

  @Test fun `intercept() intercepts calls to onPropsChanged()`() {
    val recorder = RecordingWorkflowInterceptor()
    val intercepted = recorder.intercept(TestWorkflow, TestWorkflow.session)

    val state = intercepted.onPropsChanged("old", "new", "state")

    assertEquals("old|new|state", state)
    assertEquals(listOf("BEGIN|onPropsChanged", "END|onPropsChanged"), recorder.consumeEventNames())
  }

  @Test fun `intercept() intercepts calls to render()`() {
    val recorder = RecordingWorkflowInterceptor()
    val intercepted = recorder.intercept(TestWorkflow, TestWorkflow.session)
    val fakeContext = object : RenderContext<String, String, String> {
      override val actionSink: Sink<WorkflowAction<String, String, String>> get() = fail()

      override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
        child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
        props: ChildPropsT,
        key: String,
        handler: (ChildOutputT) -> WorkflowAction<String, String, String>
      ): ChildRenderingT = fail()

      override fun runningSideEffect(
        key: String,
        sideEffect: suspend () -> Unit
      ): Unit = fail()
    }

    val rendering = intercepted.render("props", "state", fakeContext)

    assertEquals("props|state", rendering)
    assertEquals(listOf("BEGIN|onRender", "END|onRender"), recorder.consumeEventNames())
  }

  @Test fun `intercept() intercepts calls to snapshotState()`() {
    val recorder = RecordingWorkflowInterceptor()
    val intercepted = recorder.intercept(TestWorkflow, TestWorkflow.session)

    val snapshot = intercepted.snapshotState("state")

    assertEquals(Snapshot.of("state"), snapshot)
    assertEquals(
        listOf("BEGIN|onSnapshotState", "END|onSnapshotState"), recorder.consumeEventNames()
    )
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
      props: String,
      state: String,
      context: RenderContext<String, String, String>
    ): String = "$props|$state"

    override fun snapshotState(state: String): Snapshot = Snapshot.of(state)
  }
}
