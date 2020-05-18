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
    "EXPERIMENTAL_API_USAGE",
    "FunctionNaming",
    "NOTHING_TO_INLINE",
    "RemoveEmptyParenthesesFromAnnotationEntry"
)

package com.squareup.workflow.ui.compose

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import androidx.compose.Composable
import androidx.compose.Direct
import androidx.compose.Pivotal
import androidx.compose.State
import androidx.compose.onDispose
import androidx.compose.remember
import androidx.compose.state
import androidx.ui.core.CoroutineContextAmbient
import androidx.ui.core.Modifier
import androidx.ui.foundation.Box
import androidx.ui.savedinstancestate.Saver
import androidx.ui.savedinstancestate.SaverScope
import androidx.ui.savedinstancestate.UiSavedStateRegistryAmbient
import androidx.ui.savedinstancestate.savedInstanceState
import com.squareup.workflow.Snapshot
import com.squareup.workflow.Workflow
import com.squareup.workflow.diagnostic.WorkflowDiagnosticListener
import com.squareup.workflow.launchWorkflowIn
import com.squareup.workflow.ui.ViewEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okio.ByteString
import kotlin.coroutines.CoroutineContext

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * @param workflow The [Workflow] to render.
 * @param props The props to render the root workflow with. If this value changes between calls,
 * the workflow runtime will re-render with the new props.
 * @param onOutput A function that will be invoked any time the root workflow emits an output.
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 * @param content A [Composable] function that gets executed every time the root workflow spits
 * out a new rendering.
 */
@Direct
@Composable fun <PropsT, OutputT : Any, RenderingT> WorkflowContainer(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: (OutputT) -> Unit,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null,
  content: @Composable() (rendering: RenderingT) -> Unit
) {
  WorkflowContainerImpl(workflow, props, onOutput, modifier, diagnosticListener, content = content)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * @param workflow The [Workflow] to render.
 * @param onOutput A function that will be invoked any time the root workflow emits an output.
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 * @param content A [Composable] function that gets executed every time the root workflow spits
 * out a new rendering.
 */
@Composable inline fun <OutputT : Any, RenderingT> WorkflowContainer(
  workflow: Workflow<Unit, OutputT, RenderingT>,
  noinline onOutput: (OutputT) -> Unit,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null,
  noinline content: @Composable() (rendering: RenderingT) -> Unit
) {
  WorkflowContainer(workflow, Unit, onOutput, modifier, diagnosticListener, content)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * @param workflow The [Workflow] to render.
 * @param props The props to render the root workflow with. If this value changes between calls,
 * the workflow runtime will re-render with the new props.
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 * @param content A [Composable] function that gets executed every time the root workflow spits
 * out a new rendering.
 */
@Composable inline fun <PropsT, RenderingT> WorkflowContainer(
  workflow: Workflow<PropsT, Nothing, RenderingT>,
  props: PropsT,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null,
  noinline content: @Composable() (rendering: RenderingT) -> Unit
) {
  WorkflowContainer(workflow, props, {}, modifier, diagnosticListener, content)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * @param workflow The [Workflow] to render.
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 * @param content A [Composable] function that gets executed every time the root workflow spits
 * out a new rendering.
 */
@Composable inline fun <RenderingT> WorkflowContainer(
  workflow: Workflow<Unit, Nothing, RenderingT>,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null,
  noinline content: @Composable() (rendering: RenderingT) -> Unit
) {
  WorkflowContainer(workflow, Unit, {}, modifier, diagnosticListener, content)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * @param workflow The [Workflow] to render.
 * @param viewEnvironment The [ViewEnvironment] used to show the [ComposeRendering]s emitted by
 * the workflow.
 * @param props The props to render the root workflow with. If this value changes between calls,
 * the workflow runtime will re-render with the new props.
 * @param onOutput A function that will be invoked any time the root workflow emits an output.
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 */
@Direct
@Composable fun <PropsT, OutputT : Any> WorkflowContainer(
  workflow: Workflow<PropsT, OutputT, ComposeRendering>,
  viewEnvironment: ViewEnvironment,
  props: PropsT,
  onOutput: (OutputT) -> Unit,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null
) {
  WorkflowContainer(workflow, props, onOutput, modifier, diagnosticListener) { rendering ->
    rendering.render(viewEnvironment)
  }
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * @param workflow The [Workflow] to render.
 * @param viewEnvironment The [ViewEnvironment] used to show the [ComposeRendering]s emitted by
 * the workflow.
 * @param onOutput A function that will be invoked any time the root workflow emits an output.
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 */
@Composable inline fun <OutputT : Any> WorkflowContainer(
  workflow: Workflow<Unit, OutputT, ComposeRendering>,
  viewEnvironment: ViewEnvironment,
  noinline onOutput: (OutputT) -> Unit,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null
) {
  WorkflowContainer(workflow, viewEnvironment, Unit, onOutput, modifier, diagnosticListener)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * @param workflow The [Workflow] to render.
 * @param viewEnvironment The [ViewEnvironment] used to show the [ComposeRendering]s emitted by
 * the workflow.
 * @param props The props to render the root workflow with. If this value changes between calls,
 * the workflow runtime will re-render with the new props.
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 */
@Composable inline fun <PropsT> WorkflowContainer(
  workflow: Workflow<PropsT, Nothing, ComposeRendering>,
  viewEnvironment: ViewEnvironment,
  props: PropsT,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null
) {
  WorkflowContainer(workflow, viewEnvironment, props, {}, modifier, diagnosticListener)
}

/**
 * Render a [Workflow]'s renderings.
 *
 * When this function is first composed it will start a new runtime. This runtime will be restarted
 * any time [workflow], [diagnosticListener], or the `CoroutineContext`
 * changes. The runtime will be cancelled when this function stops composing.
 *
 * @param workflow The [Workflow] to render.
 * @param viewEnvironment The [ViewEnvironment] used to show the [ComposeRendering]s emitted by
 * the workflow.
 * @param diagnosticListener A [WorkflowDiagnosticListener] to configure on the runtime.
 */
@Composable inline fun WorkflowContainer(
  workflow: Workflow<Unit, Nothing, ComposeRendering>,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null
) {
  WorkflowContainer(workflow, viewEnvironment, Unit, {}, modifier, diagnosticListener)
}

/**
 * Internal version of [WorkflowContainer] that accepts extra parameters for testing.
 */
@VisibleForTesting(otherwise = PRIVATE)
@Composable internal fun <PropsT, OutputT : Any, RenderingT> WorkflowContainerImpl(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: (OutputT) -> Unit,
  modifier: Modifier = Modifier,
  diagnosticListener: WorkflowDiagnosticListener? = null,
  snapshotKey: String? = null,
  content: @Composable() (rendering: RenderingT) -> Unit
) {
  @Suppress("DEPRECATION")
  val rendering = renderAsState(
      workflow, props, onOutput, CoroutineContextAmbient.current, diagnosticListener, snapshotKey
  )

  Box(modifier = modifier) {
    content(rendering.value)
  }
}

/**
 * @param snapshotKey Allows tests to pass in a custom key to use to save/restore the snapshot from
 * the [UiSavedStateRegistryAmbient]. If null, will use the default key based on source location.
 */
@Composable private fun <PropsT, OutputT : Any, RenderingT> renderAsState(
  @Pivotal workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: (OutputT) -> Unit,
  @Pivotal coroutineContext: CoroutineContext,
  @Pivotal diagnosticListener: WorkflowDiagnosticListener?,
  snapshotKey: String?
): State<RenderingT> {
  // This can be a StateFlow once coroutines is upgraded to 1.3.6.
  val propsChannel = remember { Channel<PropsT>(capacity = CONFLATED) }
  propsChannel.offer(props)

  // Need a mutable holder for onOutput so the outputs subscriber created in the onActive block
  // will always be able to see the latest value.
  val outputCallback = remember { OutputCallback(onOutput) }
  outputCallback.onOutput = onOutput

  val renderingState = state<RenderingT?> { null }
  val snapshotState = savedInstanceState(key = snapshotKey, saver = SnapshotSaver) { null }

  // We can't use onActive/on(Pre)Commit because they won't run their callback until after this
  // function returns, and we need to run this immediately so we get the rendering synchronously.
  val workflowScope = remember {
    val coroutineScope = CoroutineScope(coroutineContext + Dispatchers.Main.immediate)
    val propsFlow = propsChannel.consumeAsFlow()
        .distinctUntilChanged()

    launchWorkflowIn(coroutineScope, workflow, propsFlow, snapshotState.value) { session ->
      session.diagnosticListener = diagnosticListener

      // Don't call onOutput directly, since out captured reference won't be changed if the
      // if a different argument is passed to observeWorkflow.
      session.outputs.onEach { outputCallback.onOutput(it) }
          .launchIn(this)

      session.renderingsAndSnapshots
          .onEach { (rendering, snapshot) ->
            renderingState.value = rendering
            snapshotState.value = snapshot
          }
          .launchIn(this)
    }

    return@remember coroutineScope
  }

  onDispose {
    workflowScope.cancel()
  }

  // The value is guaranteed to be set before returning, so this cast is fine.
  @Suppress("UNCHECKED_CAST")
  return renderingState as State<RenderingT>
}

private object SnapshotSaver : Saver<Snapshot?, ByteArray> {
  override fun SaverScope.save(value: Snapshot?): ByteArray {
    return value?.bytes?.toByteArray() ?: ByteArray(0)
  }

  override fun restore(value: ByteArray): Snapshot? {
    return value.takeUnless { it.isEmpty() }
        ?.let { bytes -> Snapshot.of(ByteString.of(*bytes)) }
  }
}

private class OutputCallback<OutputT>(var onOutput: (OutputT) -> Unit)
