@file:OptIn(ExperimentalWorkflowApi::class)

package com.squareup.workflow1.ui.compose

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
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
 * [State] object that will be updated whenever the runtime emits a new [RenderingT]. Note that
 * here, and in the rest of the documentation for this class, the "`State`" type refers to Compose's
 * snapshot [State] type, _not_ the concept of the `StateT` type in a particular workflow.
 *
 * The workflow runtime will be started when this function is first added to the composition, and
 * cancelled when it is removed or if the composition fails. The first rendering will be available
 * immediately as soon as this function returns as [State.value]. Composables that read this value
 * will automatically recompose whenever the runtime emits a new rendering. If you are driving UI
 * from the Workflow tree managed by [renderAsState] then you will probably want to pass the
 * returned [State]'s value (which is the Workflow rendering) to the [WorkflowRendering] composable.
 *
 * Note that the initial render pass will occur on whatever thread this function is called from.
 * That may be a background thread, as Compose supports performing composition on background
 * threads. Well-behaved workflows should have pure `initialState` and `render` functions, so this
 * should not be a problem. Any side effects performed by workflows using the `runningSideEffect`
 * method or Workers will be executed in [scope] as usual.
 *
 * [Snapshot]s from the runtime will automatically be saved and restored using Compose's
 * [rememberSaveable].
 *
 * ## Example
 *
 * ```
 * private val appViewRegistry = ViewRegistry(…)
 *
 * @Composable fun App(workflow: Workflow<…>, props: Props) {
 *   val scaffoldState = …
 *
 *   // Run the workflow in the current composition's coroutine scope.
 *   val rendering by workflow.renderAsState(props, onOutput = { output ->
 *     // Note that onOutput is a suspend function, so you can run animations
 *     // and call other suspend functions.
 *     scaffoldState.snackbarHostState
 *       .showSnackbar(output.toString())
 *   })
 *   val viewEnvironment = remember {
 *     ViewEnvironment(mapOf(ViewRegistry to appViewRegistry))
 *   }
 *
 *   Scaffold(…) { padding ->
 *     // Display the root rendering using the view environment's ViewRegistry.
 *     WorkflowRendering(rendering, viewEnvironment, Modifier.padding(padding))
 *   }
 * }
 * ```
 *
 * @receiver The [Workflow] to run. If the value of the receiver changes to a different [Workflow]
 * while this function is in the composition, the runtime will be restarted with the new workflow.
 * @param props The [PropsT] for the root [Workflow]. Changes to this value across different
 * compositions will cause the root workflow to re-render with the new props.
 * @param interceptors
 * An optional list of [WorkflowInterceptor]s that will wrap every workflow rendered by the runtime.
 * Interceptors will be invoked in 0-to-`length` order: the interceptor at index 0 will process the
 * workflow first, then the interceptor at index 1, etc.
 * @param scope
 * The [CoroutineScope] in which to launch the workflow runtime. If not specified, the value of
 * [rememberCoroutineScope] will be used. Any exceptions thrown in any workflows, after the initial
 * render pass, will be handled by this scope, and cancelling this scope will cancel the workflow
 * runtime and any running workers. Note that any dispatcher in this scope will _not_ be used to
 * execute the very first render pass.
 * @param onOutput A function that will be executed whenever the root [Workflow] emits an output.
 */
@Composable
public fun <PropsT, OutputT : Any, RenderingT> Workflow<PropsT, OutputT, RenderingT>.renderAsState(
  props: PropsT,
  interceptors: List<WorkflowInterceptor> = emptyList(),
  scope: CoroutineScope = rememberCoroutineScope(),
  onOutput: suspend (OutputT) -> Unit
): State<RenderingT> = renderAsState(this, scope, props, interceptors, onOutput)

/**
 * @param snapshotKey Allows tests to pass in a custom key to use to save/restore the snapshot from
 * the [LocalSaveableStateRegistry]. If null, will use the default key based on source location.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable internal fun <PropsT, OutputT : Any, RenderingT> renderAsState(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  scope: CoroutineScope,
  props: PropsT,
  interceptors: List<WorkflowInterceptor>,
  onOutput: suspend (OutputT) -> Unit,
  snapshotKey: String? = null
): State<RenderingT> {
  val snapshotState = rememberSaveable(key = snapshotKey, stateSaver = TreeSnapshotSaver) {
    mutableStateOf(null)
  }
  val updatedOnOutput by rememberUpdatedState(onOutput)

  // We can't use DisposableEffect because it won't run until the composition is successfully
  // committed, which will be after this function returns, and we need to run this immediately so we
  // get the rendering synchronously. The thread running this composition might also not be the
  // main thread or whatever thread the workflow context is configured to run on, but that should
  // be fine as long as the workflows are correctly performing side effects in effects and not their
  // render or related methods.
  // The WorkflowState object remembered here is a RememberObserver – it will automatically cancel
  // the workflow runtime when it leaves the composition or if the composition doesn't commit.
  // The remember is keyed on any values that we can't update the runtime with dynamically, and
  // therefore require completely restarting the runtime to take effect.
  val state = remember(workflow, scope, interceptors) {
    WorkflowRuntimeState<PropsT, OutputT, RenderingT>(
      workflowScope = scope,
      initialProps = props,
      snapshotState = snapshotState,
      onOutput = { updatedOnOutput(it) }
    ).apply {
      start(workflow, interceptors)
    }
  }

  // Use a side effect to update props so that it waits for the composition to commit.
  SideEffect {
    state.setProps(props)
  }

  return state.rendering
}

/**
 * State hoisted out of [renderAsState].
 */
private class WorkflowRuntimeState<PropsT, OutputT : Any, RenderingT>(
  private val workflowScope: CoroutineScope,
  initialProps: PropsT,
  private val snapshotState: MutableState<TreeSnapshot?>,
  private val onOutput: suspend (OutputT) -> Unit,
) : RememberObserver {

  private val renderingState = mutableStateOf<RenderingT?>(null)
  private val propsFlow = MutableStateFlow(initialProps)

  // The value is guaranteed to be set before returning, so this cast is fine.
  @Suppress("UNCHECKED_CAST")
  val rendering: State<RenderingT>
    get() = renderingState as State<RenderingT>

  fun start(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    interceptors: List<WorkflowInterceptor>
  ) {
    val renderings = renderWorkflowIn(
      workflow = workflow,
      scope = workflowScope,
      props = propsFlow,
      initialSnapshot = snapshotState.value,
      interceptors = interceptors,
      onOutput = onOutput
    )

    renderings
      .onEach {
        renderingState.value = it.rendering
        snapshotState.value = it.snapshot
      }
      .launchIn(workflowScope)
  }

  fun setProps(props: PropsT) {
    propsFlow.value = props
  }

  override fun onAbandoned() {
    workflowScope.cancel()
  }

  override fun onRemembered() {}

  override fun onForgotten() {
    workflowScope.cancel()
  }
}

private object TreeSnapshotSaver : Saver<TreeSnapshot?, ByteArray> {
  override fun SaverScope.save(value: TreeSnapshot?): ByteArray {
    return value?.toByteString()?.toByteArray() ?: ByteArray(0)
  }

  override fun restore(value: ByteArray): TreeSnapshot? {
    return value.takeUnless { it.isEmpty() }
      ?.let { bytes -> TreeSnapshot.parse(ByteString.of(*bytes)) }
  }
}
