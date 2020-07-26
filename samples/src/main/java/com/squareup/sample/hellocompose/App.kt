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
package com.squareup.sample.hellocompose

import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.foundation.drawBorder
import androidx.ui.foundation.shape.corner.RoundedCornerShape
import androidx.ui.graphics.Color
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.squareup.workflow.ui.compose.WorkflowContainer
import com.squareup.workflow1.SimpleLoggingWorkflowInterceptor
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
private val viewRegistry = ViewRegistry(HelloBinding)

@OptIn(WorkflowUiExperimentalApi::class)
private val viewEnvironment = ViewEnvironment(viewRegistry)

@OptIn(WorkflowUiExperimentalApi::class)
@Composable fun App() {
  MaterialTheme {
    WorkflowContainer(
        HelloWorkflow, viewEnvironment,
        modifier = Modifier.drawBorder(
            shape = RoundedCornerShape(10.dp),
            size = 10.dp,
            color = Color.Magenta
        ),
        interceptors = listOf(SimpleLoggingWorkflowInterceptor())
    )
  }
}

// This preview is broken in dev10, Compose runtime throws an ArrayIndexOutOfBoundsException.
@Preview(showBackground = true)
@Composable private fun AppPreview() {
  App()
}
