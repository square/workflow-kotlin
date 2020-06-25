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
package com.squareup.workflow.testing

import com.squareup.workflow.Workflow
import com.squareup.workflow.action
import com.squareup.workflow.renderChild
import com.squareup.workflow.renderWorkflowIn
import com.squareup.workflow.stateless
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RenderIdempotencyCheckerTest {

  @Test fun `renders tree twice`() {
    var leafRenders = 0
    var rootRenders = 0
    // Use a tree depth of at least two to test that we're not double-rendering every _workflow_.
    val leafWorkflow = Workflow.stateless<Unit, Nothing, Unit> { leafRenders++ }
    val rootWorkflow = Workflow.stateless<Unit, Nothing, Unit> {
      rootRenders++
      renderChild(leafWorkflow)
    }
    val scope = TestCoroutineScope(Job())
        .apply { pauseDispatcher() }

    renderWorkflowIn(
        rootWorkflow, scope, MutableStateFlow(Unit), interceptors = listOf(RenderIdempotencyChecker)
    ) {}
    assertEquals(2, rootRenders)
    assertEquals(2, leafRenders)
  }

  @Test fun `events sent to sink read after render are accepted`() {
    val workflow = Workflow.stateless<Unit, String, (String) -> Unit> {
      { value: String ->
        actionSink.send(action {
          setOutput(value)
        })
      }
    }
    val outputs = mutableListOf<String>()
    val renderings = renderWorkflowIn(
        workflow, CoroutineScope(Unconfined), MutableStateFlow(Unit),
        interceptors = listOf(RenderIdempotencyChecker)
    ) {
      outputs += it
    }

    assertEquals(emptyList<String>(), outputs)
    renderings.value.rendering.invoke("poke")
    assertEquals(listOf("poke"), outputs)
  }

  @Test fun `events sent to sink captured during render are accepted`() {
    val workflow = Workflow.stateless<Unit, String, (String) -> Unit> {
      val sink = actionSink
      { value: String ->
        sink.send(action {
          setOutput(value)
        })
      }
    }
    val outputs = mutableListOf<String>()
    val renderings = renderWorkflowIn(
        workflow, CoroutineScope(Unconfined), MutableStateFlow(Unit),
        interceptors = listOf(RenderIdempotencyChecker)
    ) {
      outputs += it
    }

    assertEquals(emptyList<String>(), outputs)
    renderings.value.rendering.invoke("poke")
    assertEquals(listOf("poke"), outputs)
  }
}
