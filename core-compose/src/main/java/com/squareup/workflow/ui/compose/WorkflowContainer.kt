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
@file:Suppress(
    "FunctionNaming",
    "NOTHING_TO_INLINE"
)

package com.squareup.workflow.ui.compose

import androidx.compose.Composable
import androidx.compose.remember
import androidx.ui.core.Modifier
import com.squareup.workflow.Snapshot
import com.squareup.workflow.Workflow
import com.squareup.workflow.diagnostic.WorkflowDiagnosticListener
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewFactory
import com.squareup.workflow.ui.ViewRegistry
import com.squareup.workflow.ui.plus

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.ui.savedinstancestate.UiSavedStateRegistry]. When the runtime is
 * started, if a snapshot exists in the registry, it will be used to restore the workflows.
 *
 * @param workflow The [Workflow] to render.
 * @param props The props to render the root workflow with. If this value changes between calls,
 * the workflow runtime will re-render with the new props.
 * @param onOutput A function that will be invoked any time the root workflow emits an output.
 * @param viewEnvironment The [ViewEnvironment] used to display renderings.
 * @param modifier The [Modifier] to apply to the root [ViewFactory].
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 */
@Composable fun <PropsT, OutputT : Any, RenderingT : Any> WorkflowContainer(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: (OutputT) -> Unit,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null
) {
  // Ensure ComposeRendering is in the ViewRegistry.
  val realEnvironment = remember(viewEnvironment) {
    viewEnvironment.withFactory(ComposeRendering.Factory)
  }

  val rendering = workflow.renderAsState(props, onOutput, diagnosticListener)
  WorkflowRendering(rendering.value, realEnvironment, modifier)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.ui.savedinstancestate.UiSavedStateRegistry]. When the runtime is
 * started, if a snapshot exists in the registry, it will be used to restore the workflows.
 *
 * @param workflow The [Workflow] to render.
 * @param onOutput A function that will be invoked any time the root workflow emits an output.
 * @param viewEnvironment The [ViewEnvironment] used to display renderings.
 * @param modifier The [Modifier] to apply to the root [ViewFactory].
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 */
@Composable inline fun <OutputT : Any, RenderingT : Any> WorkflowContainer(
  workflow: Workflow<Unit, OutputT, RenderingT>,
  noinline onOutput: (OutputT) -> Unit,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null
) {
  WorkflowContainer(workflow, Unit, onOutput, viewEnvironment, modifier, diagnosticListener)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.ui.savedinstancestate.UiSavedStateRegistry]. When the runtime is
 * started, if a snapshot exists in the registry, it will be used to restore the workflows.
 *
 * @param workflow The [Workflow] to render.
 * @param props The props to render the root workflow with. If this value changes between calls,
 * the workflow runtime will re-render with the new props.
 * @param viewEnvironment The [ViewEnvironment] used to display renderings.
 * @param modifier The [Modifier] to apply to the root [ViewFactory].
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 */
@Composable inline fun <PropsT, RenderingT : Any> WorkflowContainer(
  workflow: Workflow<PropsT, Nothing, RenderingT>,
  props: PropsT,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null
) {
  WorkflowContainer(workflow, props, {}, viewEnvironment, modifier, diagnosticListener)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.ui.savedinstancestate.UiSavedStateRegistry]. When the runtime is
 * started, if a snapshot exists in the registry, it will be used to restore the workflows.
 *
 * @param workflow The [Workflow] to render.
 * @param viewEnvironment The [ViewEnvironment] used to display renderings.
 * @param modifier The [Modifier] to apply to the root [ViewFactory].
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 */
@Composable inline fun <RenderingT : Any> WorkflowContainer(
  workflow: Workflow<Unit, Nothing, RenderingT>,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null
) {
  WorkflowContainer(workflow, Unit, {}, viewEnvironment, modifier, diagnosticListener)
}

private fun ViewEnvironment.withFactory(viewFactory: ViewFactory<*>): ViewEnvironment {
  return this[ViewRegistry].let { registry ->
    if (viewFactory.type !in registry.keys) {
      this + (ViewRegistry to registry + viewFactory)
    } else this
  }
}
