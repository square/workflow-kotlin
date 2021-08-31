package com.squareup.workflow1.ui

import androidx.lifecycle.SavedStateHandle
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.renderWorkflowIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * An Android `ViewModel`-friendly wrapper for [com.squareup.workflow1.renderWorkflowIn],
 * for use with a [workflow] that takes no input (that is, has `PropsT` set to [Unit]).
 *
 *    @OptIn(WorkflowUiExperimentalApi::class)
 *    class HelloWorkflowActivity : AppCompatActivity() {
 *      override fun onCreate(savedInstanceState: Bundle?) {
 *        super.onCreate(savedInstanceState)
 *
 *        val model: HelloViewModel by viewModels()
 *        setContentView(
 *          WorkflowLayout(this).apply { start(model.renderings) }
 *        )
 *      }
 *    }
 *
 *    class HelloViewModel(savedState: SavedStateHandle) : ViewModel() {
 *      @OptIn(WorkflowUiExperimentalApi::class)
 *      val renderings: StateFlow<HelloRendering> = renderWorkflowIn(
 *        workflow = HelloWorkflow,
 *        scope = this.viewModelScope,
 *        savedStateHandle = savedState
 *      )
 *    }
 *
 * @param workflow
 * The root workflow to render.
 *
 * @param scope
 * The [CoroutineScope] in which to launch the workflow runtime, typically from
 * the androidx `ViewModel.viewModelScope` extension. Any exceptions thrown
 * in any workflows, after the initial render pass, will be handled by this scope, and cancelling
 * this scope will cancel the workflow runtime and any running workers. Note that any dispatcher
 * in this scope will _not_ be used to execute the very first render pass.
 *
 * @param savedStateHandle
 * Used to restore workflow state in a new process. Typically this is the
 * `savedState: SavedStateHandle` constructor parameter of an
 * androidx [ViewModel][androidx.lifecycle.ViewModel].
 *
 * @param interceptors
 * An optional list of [WorkflowInterceptor]s that will wrap every workflow rendered by the runtime.
 * Interceptors will be invoked in 0-to-`length` order: the interceptor at index 0 will process the
 * workflow first, then the interceptor at index 1, etc.
 *
 * @param onOutput
 * A function that will be called whenever the root workflow emits an [OutputT]. This is a suspend
 * function, and is invoked synchronously within the runtime: if it suspends, the workflow runtime
 * will effectively be paused until it returns. This means that it will propagate backpressure if
 * used to forward outputs to a [Flow][kotlinx.coroutines.flow.Flow]
 * or [Channel][kotlinx.coroutines.channels.Channel], for example.
 *
 * @return
 * A [StateFlow] of [RenderingT]s that will emit any time the root workflow creates a new
 * rendering.
 */
@WorkflowUiExperimentalApi
@OptIn(ExperimentalCoroutinesApi::class)
public fun <OutputT, RenderingT> renderWorkflowIn(
  workflow: Workflow<Unit, OutputT, RenderingT>,
  scope: CoroutineScope,
  savedStateHandle: SavedStateHandle? = null,
  interceptors: List<WorkflowInterceptor> = emptyList(),
  onOutput: suspend (OutputT) -> Unit = {}
): StateFlow<RenderingT> {
  return renderWorkflowIn(
    workflow = workflow,
    scope = scope,
    props = MutableStateFlow(Unit),
    savedStateHandle = savedStateHandle,
    interceptors = interceptors,
    onOutput = onOutput
  )
}

/**
 * An Android `ViewModel`-friendly wrapper for [com.squareup.workflow1.renderWorkflowIn],
 * for use with a [workflow] that requires one input value ([prop]) to run.
 *
 *    @OptIn(WorkflowUiExperimentalApi::class)
 *    class HelloNameWorkflowActivity : AppCompatActivity() {
 *      override fun onCreate(savedInstanceState: Bundle?) {
 *        super.onCreate(savedInstanceState)
 *
 *        val model: HelloNameViewModel by viewModels()
 *        setContentView(
 *          WorkflowLayout(this).apply { start(model.renderings) }
 *        )
 *      }
 *    }
 *
 *    class HelloNameViewModel(savedState: SavedStateHandle) : ViewModel() {
 *      @OptIn(WorkflowUiExperimentalApi::class)
 *      val renderings: StateFlow<HelloRendering> = renderWorkflowIn(
 *        workflow = HelloNameWorkflow,
 *        scope = this.viewModelScope,
 *        savedStateHandle = savedState,
 *        prop = "Your name here!"
 *      )
 *    }
 *
 * @param workflow
 * The root workflow to render.
 *
 * @param scope
 * The [CoroutineScope] in which to launch the workflow runtime, typically from
 * the androidx `ViewModel.viewModelScope` extension. Any exceptions thrown
 * in any workflows, after the initial render pass, will be handled by this scope, and cancelling
 * this scope will cancel the workflow runtime and any running workers. Note that any dispatcher
 * in this scope will _not_ be used to execute the very first render pass.
 *
 * @param prop
 * Specifies the sole [PropsT] value to use to render the root workflow. To allow updates,
 * use the [renderWorkflowIn] overload with a `props: `[StateFlow]`<PropsT>` argument
 * instead of this one.
 *
 * @param savedStateHandle
 * Used to restore workflow state in a new process. Typically this is the
 * `savedState: SavedStateHandle` constructor parameter of an
 * androidx [ViewModel][androidx.lifecycle.ViewModel].
 *
 * @param interceptors
 * An optional list of [WorkflowInterceptor]s that will wrap every workflow rendered by the runtime.
 * Interceptors will be invoked in 0-to-`length` order: the interceptor at index 0 will process the
 * workflow first, then the interceptor at index 1, etc.
 *
 * @param onOutput
 * A function that will be called whenever the root workflow emits an [OutputT]. This is a suspend
 * function, and is invoked synchronously within the runtime: if it suspends, the workflow runtime
 * will effectively be paused until it returns. This means that it will propagate backpressure if
 * used to forward outputs to a [Flow][kotlinx.coroutines.flow.Flow]
 * or [Channel][kotlinx.coroutines.channels.Channel], for example.
 *
 * @return
 * A [StateFlow] of [RenderingT]s that will emit any time the root workflow creates a new
 * rendering.
 */
@WorkflowUiExperimentalApi
@OptIn(ExperimentalCoroutinesApi::class)
public fun <PropsT, OutputT, RenderingT> renderWorkflowIn(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  scope: CoroutineScope,
  prop: PropsT,
  savedStateHandle: SavedStateHandle? = null,
  interceptors: List<WorkflowInterceptor> = emptyList(),
  onOutput: suspend (OutputT) -> Unit = {}
): StateFlow<RenderingT> = renderWorkflowIn(
  workflow, scope, MutableStateFlow(prop), savedStateHandle, interceptors, onOutput
)

/**
 * An Android `ViewModel`-friendly wrapper for [com.squareup.workflow1.renderWorkflowIn],
 * for use with a [workflow] that requires input ([props]) to run.
 *
 * For example, for a workflow that uses [android.content.Intent] as its `PropsT` type,
 * you could do something like this:
 *
 *    @OptIn(WorkflowUiExperimentalApi::class)
 *    class HelloIntentsWorkflowActivity : AppCompatActivity() {
 *
 *      override fun onCreate(savedInstanceState: Bundle?) {
 *        super.onCreate(savedInstanceState)
 *
 *        val model: HelloIntentsViewModel by viewModels()
 *        model.intents.value = intent
 *
 *        setContentView(
 *          WorkflowLayout(this).apply { start(model.renderings) }
 *        )
 *      }
 *
 *      override fun onNewIntent(intent: Intent) {
 *        super.onNewIntent(intent)
 *
 *        val model: HelloIntentsViewModel by viewModels()
 *        model.intents.value = intent
 *      }
 *    }
 *
 *    class HelloIntentsViewModel(savedState: SavedStateHandle) : ViewModel() {
 *      val intents = MutableStateFlow(Intent())
 *
 *      @OptIn(WorkflowUiExperimentalApi::class)
 *      val renderings: StateFlow<HelloRendering> = renderWorkflowIn(
 *        workflow = HelloWorkflow,
 *        scope = this.viewModelScope,
 *        savedStateHandle = savedState,
 *        props = intents
 *      )
 *    }
 *
 * @param workflow
 * The root workflow to render.
 *
 * @param scope
 * The [CoroutineScope] in which to launch the workflow runtime, typically from
 * the androidx `ViewModel.viewModelScope` extension. Any exceptions thrown
 * in any workflows, after the initial render pass, will be handled by this scope, and cancelling
 * this scope will cancel the workflow runtime and any running workers. Note that any dispatcher
 * in this scope will _not_ be used to execute the very first render pass.
 *
 * @param props
 * Specifies the initial [PropsT] to use to render the root workflow, and will cause a re-render
 * when new props are emitted. If this flow completes _after_ emitting at least one value, the
 * runtime will _not_ fail or stop, it will continue running with the last-emitted input.
 * To only pass a single props value, simply create a [MutableStateFlow] with the value.
 *
 * @param savedStateHandle
 * Used to restore workflow state in a new process. Typically this is the
 * `savedState: SavedStateHandle` constructor parameter of an
 * androidx [ViewModel][androidx.lifecycle.ViewModel].
 *
 * @param interceptors
 * An optional list of [WorkflowInterceptor]s that will wrap every workflow rendered by the runtime.
 * Interceptors will be invoked in 0-to-`length` order: the interceptor at index 0 will process the
 * workflow first, then the interceptor at index 1, etc.
 *
 * @param onOutput
 * A function that will be called whenever the root workflow emits an [OutputT]. This is a suspend
 * function, and is invoked synchronously within the runtime: if it suspends, the workflow runtime
 * will effectively be paused until it returns. This means that it will propagate backpressure if
 * used to forward outputs to a [Flow][kotlinx.coroutines.flow.Flow]
 * or [Channel][kotlinx.coroutines.channels.Channel], for example.
 *
 * @return
 * A [StateFlow] of [RenderingT]s that will emit any time the root workflow creates a new
 * rendering.
 */
@WorkflowUiExperimentalApi
@OptIn(ExperimentalCoroutinesApi::class)
public fun <PropsT, OutputT, RenderingT> renderWorkflowIn(
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  scope: CoroutineScope,
  props: StateFlow<PropsT>,
  savedStateHandle: SavedStateHandle? = null,
  interceptors: List<WorkflowInterceptor> = emptyList(),
  onOutput: suspend (OutputT) -> Unit = {}
): StateFlow<RenderingT> {
  val restoredSnap = savedStateHandle?.get<PickledTreesnapshot>(KEY)?.snapshot
  val renderingsAndSnapshots = renderWorkflowIn(
    workflow, scope, props, restoredSnap, interceptors, onOutput
  )

  return renderingsAndSnapshots
    .onEach { savedStateHandle?.set(KEY, PickledTreesnapshot(it.snapshot)) }
    .map { it.rendering }
    .stateIn(scope, Eagerly, renderingsAndSnapshots.value.rendering)
}

private const val KEY = "com.squareup.workflow1.ui.renderWorkflowIn-snapshot"
