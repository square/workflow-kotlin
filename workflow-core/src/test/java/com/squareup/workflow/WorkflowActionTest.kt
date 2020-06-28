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
package com.squareup.workflow

import com.squareup.workflow.WorkflowAction.Updater
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalWorkflowApi::class)
class WorkflowActionTest {

  @Test fun `applyTo works when no output is set`() {
    val action = object : WorkflowAction<String, String?> {
      override fun Updater<String, String?>.apply() {
        nextState = "nextState: $nextState"
      }
    }
    val (nextState, output) = action.applyTo("state")
    assertEquals("nextState: state", nextState)
    assertNull(output)
  }

  @Test fun `applyTo works when null output is set`() {
    val action = object : WorkflowAction<String, String?> {
      override fun Updater<String, String?>.apply() {
        nextState = "nextState: $nextState"
        setOutput(null)
      }
    }
    val (nextState, output) = action.applyTo("state")
    assertEquals("nextState: state", nextState)
    assertNotNull(output)
    assertNull(output.value)
  }

  @Test fun `applyTo works when non-null output is set`() {
    val action = object : WorkflowAction<String, String?> {
      override fun Updater<String, String?>.apply() {
        nextState = "nextState: $nextState"
        setOutput("output")
      }
    }
    val (nextState, output) = action.applyTo("state")
    assertEquals("nextState: state", nextState)
    assertNotNull(output)
    assertEquals("output", output.value)
  }
}
