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
@file:OptIn(WorkflowUiExperimentalApi::class)
@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.workflow.ui.compose.tooling

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.squareup.workflow.ui.compose.ComposeWorkflow
import com.squareup.workflow1.Sink
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Draws this [ComposeWorkflow] using a special preview [ViewRegistry].
 *
 * Use inside `@Preview` Composable functions.
 *
 * *Note: [props] must be the `PropsT` of this [ComposeWorkflow], even though the type system does
 * not enforce this constraint. This is due to a Compose compiler bug tracked
 * [here](https://issuetracker.google.com/issues/156527332).*
 *
 * @param modifier [Modifier] that will be applied to this [ViewFactory].
 * @param placeholderModifier [Modifier] that will be applied to any nested renderings this factory
 * shows.
 * @param viewEnvironmentUpdater Function that configures the [ViewEnvironment] passed to this
 * factory.
 */
@Composable fun <PropsT> ComposeWorkflow<PropsT, *>.preview(
  props: PropsT,
  modifier: Modifier = Modifier,
  placeholderModifier: Modifier = Modifier,
  viewEnvironmentUpdater: ((ViewEnvironment) -> ViewEnvironment)? = null
) {
  val previewEnvironment = previewViewEnvironment(placeholderModifier, viewEnvironmentUpdater)
  Box(modifier = modifier) {
    render(props, NoopSink, previewEnvironment)
  }
}

private object NoopSink : Sink<Any> {
  override fun send(value: Any) = Unit
}
