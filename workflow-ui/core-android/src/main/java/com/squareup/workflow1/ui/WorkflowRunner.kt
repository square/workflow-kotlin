@file:Suppress("LongParameterList")

package com.squareup.workflow1.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.ui.WorkflowRunner.Config
import com.squareup.workflow1.ui.WorkflowRunnerViewModel.SnapshotSaver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

/**
 * Uses a [Workflow] and a [ViewRegistry] to drive a [WorkflowLayout].
 *
 * It is simplest to use [Activity.setContentWorkflow][setContentWorkflow]
 * or subclass [WorkflowFragment] rather than instantiate a [WorkflowRunner] directly.
 */
@WorkflowUiExperimentalApi
public interface WorkflowRunner<out OutputT> {

  /**
   * A stream of the rendering values emitted by the running [Workflow].
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  public val renderings: StateFlow<Any>

  /**
   * Returns the next [OutputT] value emitted by the workflow. Throws the cancellation
   * exception if the workflow was cancelled before emitting.
   *
   */
  public suspend fun receiveOutput(): OutputT

  /**
   * @param interceptors An optional list of [WorkflowInterceptor]s that will wrap every workflow
   * rendered by the runtime.
   */
  @OptIn(ExperimentalCoroutinesApi::class, ExperimentalWorkflowApi::class)
  public class Config<PropsT, OutputT>(
    public val workflow: Workflow<PropsT, OutputT, Any>,
    public val props: StateFlow<PropsT>,
    public val dispatcher: CoroutineDispatcher,
    public val interceptors: List<WorkflowInterceptor>
  ) {
    /**
     * @param interceptors An optional list of [WorkflowInterceptor]s that will wrap every workflow
     * rendered by the runtime.
     */
    public constructor(
      workflow: Workflow<PropsT, OutputT, Any>,
      props: PropsT,
      dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
      interceptors: List<WorkflowInterceptor> = emptyList()
    ) : this(workflow, MutableStateFlow(props), dispatcher, interceptors)
  }

  public companion object {
    /**
     * @param interceptors An optional list of [WorkflowInterceptor]s that will wrap every workflow
     * rendered by the runtime.
     */
    @OptIn(ExperimentalWorkflowApi::class)
    @Suppress("FunctionName")
    public fun <OutputT> Config(
      workflow: Workflow<Unit, OutputT, Any>,
      dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
      interceptors: List<WorkflowInterceptor> = emptyList()
    ): Config<Unit, OutputT> = Config(workflow, Unit, dispatcher, interceptors)

    /**
     * Returns an instance of [WorkflowRunner] tied to the
     * [Lifecycle][androidx.lifecycle.Lifecycle] of the given [activity].
     *
     * It's probably more convenient to use [FragmentActivity.setContentWorkflow]
     * rather than calling this method directly.
     *
     * @param configure function defining the root workflow and its environment. Called only
     * once per [lifecycle][FragmentActivity.getLifecycle], and always called from the UI thread.
     */
    public fun <PropsT, OutputT> startWorkflow(
      activity: FragmentActivity,
      configure: () -> Config<PropsT, OutputT>
    ): WorkflowRunner<OutputT> {
      val factory = WorkflowRunnerViewModel.Factory(
        SnapshotSaver.fromSavedStateRegistry(activity.savedStateRegistry), configure
      )

      @Suppress("UNCHECKED_CAST")
      return ViewModelProvider(activity, factory)[WorkflowRunnerViewModel::class.java]
        as WorkflowRunner<OutputT>
    }

    /**
     * Returns an instance of [WorkflowRunner] tied to the
     * [Lifecycle][androidx.lifecycle.Lifecycle] of the given [fragment].
     *
     * It's probably more convenient to subclass [WorkflowFragment] rather than calling
     * this method directly.
     *
     * @param configure function defining the root workflow and its environment. Called only
     * once per [lifecycle][Fragment.getLifecycle], and always called from the UI thread.
     */
    public fun <PropsT, OutputT> startWorkflow(
      fragment: Fragment,
      configure: () -> Config<PropsT, OutputT>
    ): WorkflowRunner<OutputT> {
      val factory = WorkflowRunnerViewModel.Factory(
        SnapshotSaver.fromSavedStateRegistry(fragment.savedStateRegistry), configure
      )

      @Suppress("UNCHECKED_CAST")
      return ViewModelProvider(fragment, factory)[WorkflowRunnerViewModel::class.java]
        as WorkflowRunner<OutputT>
    }
  }
}

/**
 * Call this method from [FragmentActivity.onCreate], instead of [FragmentActivity.setContentView].
 * It creates a [WorkflowRunner] for this activity, if one doesn't already exist, and
 * sets a view driven by that model as the content view.
 *
 * @param viewEnvironment provides the [ViewRegistry] used to display workflow renderings.
 *
 * @param configure function defining the root workflow and its environment. Called only
 * once per [lifecycle][FragmentActivity.getLifecycle], and always called from the UI thread.
 *
 * @param onResult function called with the output emitted by the root workflow, handy for
 * passing to [FragmentActivity.setResult]. Called only while the activity is active, and
 * always called from the UI thread.
 */
@WorkflowUiExperimentalApi
public fun <PropsT, OutputT> FragmentActivity.setContentWorkflow(
  viewEnvironment: ViewEnvironment = ViewEnvironment(),
  configure: () -> Config<PropsT, OutputT>,
  onResult: (OutputT) -> Unit
): WorkflowRunner<OutputT> {
  val runner = WorkflowRunner.startWorkflow(this, configure)
  val layout = WorkflowLayout(this@setContentWorkflow).apply {
    id = R.id.workflow_layout
    start(runner.renderings, viewEnvironment)
  }

  lifecycleScope.launchWhenStarted {
    while (isActive) {
      onResult(runner.receiveOutput())
    }
  }

  this.setContentView(layout)

  return runner
}

/**
 * Call this method from [FragmentActivity.onCreate], instead of [FragmentActivity.setContentView].
 * It creates a [WorkflowRunner] for this activity, if one doesn't already exist, and
 * sets a view driven by that model as the content view.
 *
 * @param registry used to display workflow renderings.
 *
 * @param configure function defining the root workflow and its environment. Called only
 * once per [lifecycle][FragmentActivity.getLifecycle], and always called from the UI thread.
 *
 * @param onResult function called with the first (and only) output emitted by the root workflow,
 * handy for passing to [FragmentActivity.setResult]. The workflow is ended once it emits any
 * values, so this is also a good place from which to call [FragmentActivity.finish]. Called
 * only while the activity is active, and always called from the UI thread.
 */
@WorkflowUiExperimentalApi
public fun <PropsT, OutputT> FragmentActivity.setContentWorkflow(
  registry: ViewRegistry,
  configure: () -> Config<PropsT, OutputT>,
  onResult: (OutputT) -> Unit
): WorkflowRunner<OutputT> =
  setContentWorkflow(ViewEnvironment(mapOf(ViewRegistry to registry)), configure, onResult)

/**
 * For workflows that produce no output, call this method from [FragmentActivity.onCreate]
 * instead of [FragmentActivity.setContentView].
 * It creates a [WorkflowRunner] for this activity, if one doesn't already exist, and
 * sets a view driven by that model as the content view.
 *
 * @param viewEnvironment provides the [ViewRegistry] used to display workflow renderings.
 *
 * @param configure function defining the root workflow and its environment. Called only
 * once per [lifecycle][FragmentActivity.getLifecycle], and always called from the UI thread.
 */
@WorkflowUiExperimentalApi
public fun <PropsT> FragmentActivity.setContentWorkflow(
  viewEnvironment: ViewEnvironment = ViewEnvironment(),
  configure: () -> Config<PropsT, Nothing>
): WorkflowRunner<Nothing> = setContentWorkflow(viewEnvironment, configure) {}

/**
 * For workflows that produce no output, call this method from [FragmentActivity.onCreate]
 * instead of [FragmentActivity.setContentView].
 * It creates a [WorkflowRunner] for this activity, if one doesn't already exist, and
 * sets a view driven by that model as the content view.
 *
 * @param registry used to display workflow renderings.
 *
 * @param configure function defining the root workflow and its environment. Called only
 * once per [lifecycle][FragmentActivity.getLifecycle], and always called from the UI thread.
 */
@WorkflowUiExperimentalApi
public fun <PropsT> FragmentActivity.setContentWorkflow(
  registry: ViewRegistry,
  configure: () -> Config<PropsT, Nothing>
): WorkflowRunner<Nothing> =
  setContentWorkflow(ViewEnvironment(mapOf(ViewRegistry to registry)), configure) {}
