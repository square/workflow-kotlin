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
package com.squareup.workflow1.testing

import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.identifier
import com.squareup.workflow1.mapRendering
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.stateless
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@OptIn(ExperimentalWorkflowApi::class)
class OperatorTestingTest {

  @Test fun stuff() {
    val child = Workflow.stateless<Unit, Nothing, Int> { fail() }
    val workflow = Workflow.stateless<Unit, Nothing, String> {
      renderChild(child.mapRendering { it.toString() })
    }

    workflow.testRender(Unit)
        .expectWorkflow(child.identifier, rendering = 42)
        .render {
          assertEquals("42", it)
        }
  }
}
