package com.squareup.workflow1.ui

import android.view.View
import com.squareup.workflow1.ui.WorkflowViewState.New
import com.squareup.workflow1.ui.WorkflowViewState.Started

/**
 * [View tag][View.setTag] that holds the functions and state backing [View.showRendering], etc.
 */
@WorkflowUiExperimentalApi
@PublishedApi
internal sealed class WorkflowViewState<out RenderingT : Any> {
  @PublishedApi
  internal abstract val showing: RenderingT
  abstract val environment: ViewEnvironment
  abstract val showRendering: ViewShowRendering<RenderingT>

  /** [bindShowRendering] has been called, [start] has not. */
  data class New<out RenderingT : Any>(
    override val showing: RenderingT,
    override val environment: ViewEnvironment,
    override val showRendering: ViewShowRendering<RenderingT>,

    val starter: (View) -> Unit = { view ->
      @Suppress("DEPRECATION")
      view.showRendering(view.getRendering()!!, view.environment!!)
    }
  ) : WorkflowViewState<RenderingT>()

  /** [start] has been called. It's safe to call [showRendering] now. */
  data class Started<out RenderingT : Any>(
    override val showing: RenderingT,
    override val environment: ViewEnvironment,
    override val showRendering: ViewShowRendering<RenderingT>
  ) : WorkflowViewState<RenderingT>()
}

@WorkflowUiExperimentalApi
@PublishedApi
internal val View.workflowViewStateOrNull: WorkflowViewState<*>?
  get() = getTag(R.id.legacy_workflow_view_state) as? WorkflowViewState<*>

@WorkflowUiExperimentalApi
internal var View.workflowViewState: WorkflowViewState<*>
  get() = workflowViewStateOrNull ?: error(
    "Expected $this to have been built by a ViewFactory. " +
      "Perhaps the factory did not call View.bindShowRendering."
  )
  set(value) = setTag(R.id.legacy_workflow_view_state, value)

@WorkflowUiExperimentalApi internal val View.workflowViewStateAsNew: New<*>
  get() = workflowViewState as? New<*> ?: error(
    "Expected $this to be un-started, but View.start() has been called"
  )

@WorkflowUiExperimentalApi internal val View.workflowViewStateAsStarted: Started<*>
  get() = workflowViewState as? Started<*> ?: error(
    "Expected $this to have been started, but View.start() has not been called"
  )
