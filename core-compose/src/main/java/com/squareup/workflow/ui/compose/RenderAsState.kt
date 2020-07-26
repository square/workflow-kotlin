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
@file:Suppress("NOTHING_TO_INLINE")

package com.squareup.workflow.ui.compose

import androidx.annotation.VisibleForTesting
import androidx.compose.Composable
import androidx.compose.CompositionLifecycleObserver
import androidx.compose.MutableState
import androidx.compose.State
import androidx.compose.mutableStateOf
import androidx.compose.remember
import androidx.ui.core.CoroutineContextAmbient
import androidx.ui.core.Ref
import androidx.ui.savedinstancestate.Saver
import androidx.ui.savedinstancestate.SaverScope
import androidx.ui.savedinstancestate.UiSavedStateRegistryAmbient
import androidx.ui.savedinstancestate.savedInstanceState
import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.renderWorkflowIn
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okio.ByteString
import kotlin.coroutines.CoroutineContext

/**
 * Runs this [Workflow] as long as this composable is part of the composition, and returns a
 * [State] object that will be updated whenever the runtime emits a new [RenderingT].
 *
 * The workflow runtime will be started when this function is first added to the composition, and
 * cancelled when it is removed. The first rendering will be available immediately as soon as this
 * function returns, as [State.value]. Composables that read this value will automatically recompose
 * whenever the runtime emits a new rendering.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.ui.savedinstancestate.UiSavedStateRegistry]. When the runtime is
 * started, if a snapshot exists in the registry, it will be used to restore the workflows.
 *
 * @receiver The [Workflow] to run. If the value of the receiver changes to a different [Workflow]
 * while this function is in the composition, the runtime will be restarted with the new workflow.
 * @param props The [PropsT] for the root [Workflow]. Changes to this value across different
 * compositions will cause the root workflow to re-render with the new props.
 * @param onOutput A function that will be executed whenever the root [Workflow] emits an output.
 * @param diagnosticListener An optional [WorkflowDiagnosticListener] to start the runtime with. If
 * this value changes while this function is in the composition, the runtime will be restarted.
 */
@OptIn(ExperimentalWorkflowApi::class)
@WorkflowUiExperimentalApi
@Composable
fun <PropsT, OutputT : Any, RenderingT> Workflow<PropsT, OutputT, RenderingT>.renderAsState(
  props: PropsT,
  onOutput: (OutputT) -> Unit,
  interceptors: List<WorkflowInterceptor> = emptyList()
): State<RenderingT> = renderAsStateImpl(this, props, onOutput, interceptors)

/**
 * Runs this [Workflow] as long as this composable is part of the composition, and returns a
 * [State] object that will be updated whenever the runtime emits a new [RenderingT].
 *
 * The workflow runtime will be started when this function is first added to the composition, and
 * cancelled when it is removed. The first rendering will be available immediately as soon as this
 * function returns, as [State.value]. Composables that read this value will automatically recompose
 * whenever the runtime emits a new rendering.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.ui.savedinstancestate.UiSavedStateRegistry]. When the runtime is
 * started, if a snapshot exists in the registry, it will be used to restore the workflows.
 *
 * @receiver The [Workflow] to run. If the value of the receiver changes to a different [Workflow]
 * while this function is in the composition, the runtime will be restarted with the new workflow.
 * @param onOutput A function that will be executed whenever the root [Workflow] emits an output.
 * @param diagnosticListener An optional [WorkflowDiagnosticListener] to start the runtime with. If
 * this value changes while this function is in the composition, the runtime will be restarted.
 */
@OptIn(ExperimentalWorkflowApi::class)
@WorkflowUiExperimentalApi
@Composable
inline fun <OutputT : Any, RenderingT> Workflow<Unit, OutputT, RenderingT>.renderAsState(
  noinline onOutput: (OutputT) -> Unit,
  interceptors: List<WorkflowInterceptor> = emptyList()
): State<RenderingT> = renderAsState(Unit, onOutput, interceptors)

/**
 * Runs this [Workflow] as long as this composable is part of the composition, and returns a
 * [State] object that will be updated whenever the runtime emits a new [RenderingT].
 *
 * The workflow runtime will be started when this function is first added to the composition, and
 * cancelled when it is removed. The first rendering will be available immediately as soon as this
 * function returns, as [State.value]. Composables that read this value will automatically recompose
 * whenever the runtime emits a new rendering.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.ui.savedinstancestate.UiSavedStateRegistry]. When the runtime is
 * started, if a snapshot exists in the registry, it will be used to restore the workflows.
 *
 * @receiver The [Workflow] to run. If the value of the receiver changes to a different [Workflow]
 * while this function is in the composition, the runtime will be restarted with the new workflow.
 * @param props The [PropsT] for the root [Workflow]. Changes to this value across different
 * compositions will cause the root workflow to re-render with the new props.
 * @param diagnosticListener An optional [WorkflowDiagnosticListener] to start the runtime with. If
 * this value changes while this function is in the composition, the runtime will be restarted.
 */
@OptIn(ExperimentalWorkflowApi::class)
@WorkflowUiExperimentalApi
@Composable
inline fun <PropsT, RenderingT> Workflow<PropsT, Nothing, RenderingT>.renderAsState(
  props: PropsT,
  interceptors: List<WorkflowInterceptor> = emptyList()
): State<RenderingT> = renderAsState(props, {}, interceptors)

/**
 * Runs this [Workflow] as long as this composable is part of the composition, and returns a
 * [State] object that will be updated whenever the runtime emits a new [RenderingT].
 *
 * The workflow runtime will be started when this function is first added to the composition, and
 * cancelled when it is removed. The first rendering will be available immediately as soon as this
 * function returns, as [State.value]. Composables that read this value will automatically recompose
 * whenever the runtime emits a new rendering.
 *
 * [Snapshot]s from the runtime will automatically be saved to the current
 * [UiSavedStateRegistry][androidx.ui.savedinstancestate.UiSavedStateRegistry]. When the runtime is
 * started, if a snapshot exists in the registry, it will be used to restore the workflows.
 *
 * @receiver The [Workflow] to run. If the value of the receiver changes to a different [Workflow]
 * while this function is in the composition, the runtime will be restarted with the new workflow.
 * @param diagnosticListener An optional [WorkflowDiagnosticListener] to start the runtime with. If
 * this value changes while this function is in the composition, the runtime will be restarted.
 */
@OptIn(ExperimentalWorkflowApi::class)
@WorkflowUiExperimentalApi
@Composable
inline fun <RenderingT> Workflow<Unit, Nothing, RenderingT>.renderAsState(
  interceptors: List<WorkflowInterceptor> = emptyList()
): State<RenderingT> = renderAsState(Unit, {}, interceptors)

/**
 * @param snapshotKey Allows tests to pass in a custom key to use to save/restore the snapshot from
 * the [UiSavedStateRegistryAmbient]. If null, will use the default key based on source location.
 */
@ExperimentalWorkflowApi
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable internal fun <PropsT, OutputT : Any, RenderingT> renderAsStateImpl(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: (OutputT) -> Unit,
  interceptors: List<WorkflowInterceptor>,
  snapshotKey: String? = null
): State<RenderingT> {
  @Suppress("DEPRECATION")
  val coroutineContext = CoroutineContextAmbient.current + Dispatchers.Main.immediate
  val snapshotState = savedInstanceState(key = snapshotKey, saver = SnapshotSaver) { null }

  val outputRef = remember { Ref<(OutputT) -> Unit>() }
  outputRef.value = onOutput

  // We can't use onActive/on(Pre)Commit because they won't run their callback until after this
  // function returns, and we need to run this immediately so we get the rendering synchronously.
  val state = remember(coroutineContext, workflow, interceptors) {
    WorkflowState(coroutineContext, workflow, props, outputRef, snapshotState, interceptors)
  }
  state.setProps(props)

  return state.rendering
}

@Suppress("EXPERIMENTAL_API_USAGE")
@OptIn(ExperimentalWorkflowApi::class)
private class WorkflowState<PropsT, OutputT : Any, RenderingT>(
  coroutineContext: CoroutineContext,
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  initialProps: PropsT,
  private val outputRef: Ref<(OutputT) -> Unit>,
  private val snapshotState: MutableState<TreeSnapshot?>,
  interceptors: List<WorkflowInterceptor>
) : CompositionLifecycleObserver {

  private val workflowScope = CoroutineScope(coroutineContext)
  private val renderingState = mutableStateOf<RenderingT?>(null)

  private val propsFlow = MutableStateFlow(initialProps)

  // The value is guaranteed to be set before returning, so this cast is fine.
  @Suppress("UNCHECKED_CAST")
  val rendering: State<RenderingT>
    get() = renderingState as State<RenderingT>

  init {
    val renderings =
      renderWorkflowIn(
          workflow, workflowScope, propsFlow, snapshotState.value ?: TreeSnapshot.NONE, interceptors
      ) { output ->
        outputRef.value!!.invoke(output)
      }

    renderings
        .onEach { (rendering, snapshot) ->
          renderingState.value = rendering
          snapshotState.value = snapshot
        }
        .launchIn(workflowScope)
  }

  fun setProps(props: PropsT) {
    propsFlow.value = props
  }

  override fun onEnter() {}

  override fun onLeave() {
    workflowScope.cancel()
  }
}

private object SnapshotSaver : Saver<TreeSnapshot?, ByteArray> {
  override fun SaverScope.save(value: TreeSnapshot?): ByteArray = value?.toByteString()
      ?.toByteArray()
      ?: ByteArray(0)

  override fun restore(value: ByteArray): TreeSnapshot? = value.takeUnless { it.isEmpty() }
      ?.let { bytes -> TreeSnapshot.parse(ByteString.of(*bytes)) }
}
