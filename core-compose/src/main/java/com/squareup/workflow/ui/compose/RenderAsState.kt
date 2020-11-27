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
@file:OptIn(ExperimentalWorkflowApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.squareup.workflow.ui.compose

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLifecycleObserver
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.savedinstancestate.Saver
import androidx.compose.runtime.savedinstancestate.SaverScope
import androidx.compose.runtime.savedinstancestate.rememberSavedInstanceState
import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.renderWorkflowIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okio.ByteString

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
 * [UiSavedStateRegistry][androidx.compose.runtime.savedinstancestate.UiSavedStateRegistry]. When
 * the runtime is started, if a snapshot exists in the registry, it will be used to restore the
 * workflows.
 *
 * @receiver The [Workflow] to run. If the value of the receiver changes to a different [Workflow]
 * while this function is in the composition, the runtime will be restarted with the new workflow.
 * @param props The [PropsT] for the root [Workflow]. Changes to this value across different
 * compositions will cause the root workflow to re-render with the new props.
 * @param onOutput A function that will be executed whenever the root [Workflow] emits an output.
 * @param interceptors An optional list of [WorkflowInterceptor]s that will wrap every workflow
 * rendered by the runtime. Interceptors will be invoked in 0-to-length order: the interceptor at
 * index 0 will process the workflow first, then the interceptor at index 1, etc.
 */
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
 * [UiSavedStateRegistry][androidx.compose.runtime.savedinstancestate.UiSavedStateRegistry]. When
 * the runtime is started, if a snapshot exists in the registry, it will be used to restore the
 * workflows.
 *
 * @receiver The [Workflow] to run. If the value of the receiver changes to a different [Workflow]
 * while this function is in the composition, the runtime will be restarted with the new workflow.
 * @param onOutput A function that will be executed whenever the root [Workflow] emits an output.
 * @param interceptors An optional list of [WorkflowInterceptor]s that will wrap every workflow
 * rendered by the runtime. Interceptors will be invoked in 0-to-length order: the interceptor at
 * index 0 will process the workflow first, then the interceptor at index 1, etc.
 */
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
 * [UiSavedStateRegistry][androidx.compose.runtime.savedinstancestate.UiSavedStateRegistry]. When
 * the runtime is started, if a snapshot exists in the registry, it will be used to restore the
 * workflows.
 *
 * @receiver The [Workflow] to run. If the value of the receiver changes to a different [Workflow]
 * while this function is in the composition, the runtime will be restarted with the new workflow.
 * @param props The [PropsT] for the root [Workflow]. Changes to this value across different
 * compositions will cause the root workflow to re-render with the new props.
 * @param interceptors An optional list of [WorkflowInterceptor]s that will wrap every workflow
 * rendered by the runtime. Interceptors will be invoked in 0-to-length order: the interceptor at
 * index 0 will process the workflow first, then the interceptor at index 1, etc.
 */
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
 * [UiSavedStateRegistry][androidx.compose.runtime.savedinstancestate.UiSavedStateRegistry]. When
 * the runtime is started, if a snapshot exists in the registry, it will be used to restore the
 * workflows.
 *
 * @receiver The [Workflow] to run. If the value of the receiver changes to a different [Workflow]
 * while this function is in the composition, the runtime will be restarted with the new workflow.
 * @param interceptors An optional list of [WorkflowInterceptor]s that will wrap every workflow
 * rendered by the runtime. Interceptors will be invoked in 0-to-length order: the interceptor at
 * index 0 will process the workflow first, then the interceptor at index 1, etc.
 */
@Composable
inline fun <RenderingT> Workflow<Unit, Nothing, RenderingT>.renderAsState(
  interceptors: List<WorkflowInterceptor> = emptyList()
): State<RenderingT> = renderAsState(Unit, {}, interceptors)

/**
 * @param snapshotKey Allows tests to pass in a custom key to use to save/restore the snapshot from
 * the [UiSavedStateRegistryAmbient]. If null, will use the default key based on source location.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable internal fun <PropsT, OutputT : Any, RenderingT> renderAsStateImpl(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: (OutputT) -> Unit,
  interceptors: List<WorkflowInterceptor>,
  snapshotKey: String? = null
): State<RenderingT> {
  // TODO pretty sure rememberCoroutineScope() uses an async Handler so we don't need to use Main.immediate anymore.
  // TODO Pass Dispatchers.Main.immediate and merge two scope vals when this bug is fixed:
  //  https://issuetracker.google.com/issues/165674304
  // val baseScope = rememberCoroutineScope()
  // val workflowScope = remember { baseScope + Dispatchers.Main.immediate }
  val workflowScope = rememberCoroutineScope()
  val outputState = rememberUpdatedState(onOutput)
  val snapshotHolder = rememberSavedInstanceState(key = snapshotKey, saver = TreeSnapshotHolder) {
    TreeSnapshotHolder()
  }

  // We can't use onActive/on(Pre)Commit because they won't run their callback until after this
  // function returns, and we need to run this immediately so we get the rendering synchronously.
  val state = remember(workflow, interceptors) {
    WorkflowState(workflowScope, workflow, props, outputState, snapshotHolder, interceptors)
        .apply { start() }
  }
  state.setProps(props)

  return state.rendering
}

@Suppress("EXPERIMENTAL_API_USAGE")
private class WorkflowState<PropsT, OutputT : Any, RenderingT>(
  private val workflowScope: CoroutineScope,
  private val workflow: Workflow<PropsT, OutputT, RenderingT>,
  initialProps: PropsT,
  private val outputState: State<(OutputT) -> Unit>,
  private val snapshotHolder: TreeSnapshotHolder,
  private val interceptors: List<WorkflowInterceptor>
) : CompositionLifecycleObserver {

  private val renderingState = mutableStateOf<RenderingT?>(null)
  private val propsFlow = MutableStateFlow(initialProps)

  // The value is guaranteed to be set before returning, so this cast is fine.
  @Suppress("UNCHECKED_CAST")
  val rendering: State<RenderingT>
    get() = renderingState as State<RenderingT>

  fun start() {
    println("OMG WorkflowState starting")
    val renderingsFlow = renderWorkflowIn(
        workflow, workflowScope, propsFlow, snapshotHolder.snapshot, interceptors
    ) { output ->
      outputState.value.invoke(output)
    }

    renderingsFlow
        .onEach { (rendering, snapshot) ->
          renderingState.value = rendering
          snapshotHolder.snapshot = snapshot
        }
        .launchIn(workflowScope)
  }

  fun setProps(props: PropsT) {
    propsFlow.value = props
  }

  override fun onEnter() {
    // Nothing to do.
    println("OMG WorkflowState onEnter")
  }

  override fun onLeave() {
    workflowScope.cancel()
  }
}

private class TreeSnapshotHolder {

  var snapshot: TreeSnapshot? = null

  companion object : Saver<TreeSnapshotHolder, ByteArray> {
    override fun SaverScope.save(value: TreeSnapshotHolder): ByteArray? {
      return value.snapshot?.toByteString()?.toByteArray()
    }

    override fun restore(value: ByteArray): TreeSnapshotHolder {
      return value.takeUnless { it.isEmpty() }
          ?.let { bytes -> TreeSnapshot.parse(ByteString.of(*bytes)) }
          .let { TreeSnapshotHolder().apply { snapshot = it } }
    }
  }
}
