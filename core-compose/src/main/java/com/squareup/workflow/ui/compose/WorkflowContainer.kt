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
@file:OptIn(ExperimentalWorkflowApi::class, WorkflowUiExperimentalApi::class)
@file:Suppress(
    "FunctionNaming",
    "NOTHING_TO_INLINE"
)

package com.squareup.workflow.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.plus

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [interceptors], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.compose.runtime.savedinstancestate.UiSavedStateRegistry]. When
 * the runtime is started, if a snapshot exists in the registry, it will be used to restore the
 * workflows.
 *
 * @param workflow The [Workflow] to render.
 * @param props The props to render the root workflow with. If this value changes between calls,
 * the workflow runtime will re-render with the new props.
 * @param onOutput A function that will be invoked any time the root workflow emits an output.
 * @param viewEnvironment The [ViewEnvironment] used to display renderings.
 * @param modifier The [Modifier] to apply to the root [ViewFactory].
 * @param interceptors An optional list of [WorkflowInterceptor]s that will wrap every workflow
 * rendered by the runtime. Interceptors will be invoked in 0-to-length order: the interceptor at
 * index 0 will process the workflow first, then the interceptor at index 1, etc.
 */
@Composable fun <PropsT, OutputT : Any, RenderingT : Any> WorkflowContainer(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: (OutputT) -> Unit,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier,
  interceptors: List<WorkflowInterceptor> = emptyList()
) {
  // Ensure ComposeRendering is in the ViewRegistry.
  val realEnvironment = remember(viewEnvironment) {
    viewEnvironment.withFactory(ComposeRendering.Factory)
  }

  val rendering = workflow.renderAsState(props, onOutput, interceptors)
  WorkflowRendering(rendering.value, realEnvironment, modifier)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [interceptors], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.compose.runtime.savedinstancestate.UiSavedStateRegistry]. When
 * the runtime is started, if a snapshot exists in the registry, it will be used to restore the
 * workflows.
 *
 * @param workflow The [Workflow] to render.
 * @param onOutput A function that will be invoked any time the root workflow emits an output.
 * @param viewEnvironment The [ViewEnvironment] used to display renderings.
 * @param modifier The [Modifier] to apply to the root [ViewFactory].
 * @param interceptors An optional list of [WorkflowInterceptor]s that will wrap every workflow
 * rendered by the runtime. Interceptors will be invoked in 0-to-length order: the interceptor at
 * index 0 will process the workflow first, then the interceptor at index 1, etc.
 */
@Composable inline fun <OutputT : Any, RenderingT : Any> WorkflowContainer(
  workflow: Workflow<Unit, OutputT, RenderingT>,
  noinline onOutput: (OutputT) -> Unit,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier,
  interceptors: List<WorkflowInterceptor> = emptyList()
) {
  WorkflowContainer(workflow, Unit, onOutput, viewEnvironment, modifier, interceptors)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [interceptors], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.compose.runtime.savedinstancestate.UiSavedStateRegistry]. When
 * the runtime is started, if a snapshot exists in the registry, it will be used to restore the
 * workflows.
 *
 * @param workflow The [Workflow] to render.
 * @param props The props to render the root workflow with. If this value changes between calls,
 * the workflow runtime will re-render with the new props.
 * @param viewEnvironment The [ViewEnvironment] used to display renderings.
 * @param modifier The [Modifier] to apply to the root [ViewFactory].
 * @param interceptors An optional list of [WorkflowInterceptor]s that will wrap every workflow
 * rendered by the runtime. Interceptors will be invoked in 0-to-length order: the interceptor at
 * index 0 will process the workflow first, then the interceptor at index 1, etc.
 */
@Composable inline fun <PropsT, RenderingT : Any> WorkflowContainer(
  workflow: Workflow<PropsT, Nothing, RenderingT>,
  props: PropsT,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier,
  interceptors: List<WorkflowInterceptor> = emptyList()
) {
  WorkflowContainer(workflow, props, {}, viewEnvironment, modifier, interceptors)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [interceptors], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.compose.runtime.savedinstancestate.UiSavedStateRegistry]. When
 * the runtime is started, if a snapshot exists in the registry, it will be used to restore the
 * workflows.
 *
 * @param workflow The [Workflow] to render.
 * @param viewEnvironment The [ViewEnvironment] used to display renderings.
 * @param modifier The [Modifier] to apply to the root [ViewFactory].
 * @param interceptors An optional list of [WorkflowInterceptor]s that will wrap every workflow
 * rendered by the runtime. Interceptors will be invoked in 0-to-length order: the interceptor at
 * index 0 will process the workflow first, then the interceptor at index 1, etc.
 */
@Composable inline fun <RenderingT : Any> WorkflowContainer(
  workflow: Workflow<Unit, Nothing, RenderingT>,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier,
  interceptors: List<WorkflowInterceptor> = emptyList()
) {
  WorkflowContainer(workflow, Unit, {}, viewEnvironment, modifier, interceptors)
}

private fun ViewEnvironment.withFactory(viewFactory: ViewFactory<*>): ViewEnvironment {
  return this[ViewRegistry].let { registry ->
    if (viewFactory.type !in registry.keys) {
      this + (ViewRegistry to registry + viewFactory)
    } else this
  }
}
