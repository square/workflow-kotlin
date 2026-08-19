package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.presenter.Presenter
import com.squareup.workflow1.presenter.PresenterComposable
import com.squareup.workflow1.presenter.PresenterModifier

/**
 * Emits a node containing the latest rendering of [workflow] in the default slot.
 */
@Composable
@PresenterComposable
internal fun <P, O, R> PresentWorkflow(
  workflow: Workflow<P, O, R>,
  props: P,
  onOutput: ((O) -> Unit)?,
  config: WorkflowComposableRuntimeConfig,
  parentSession: WorkflowSession?,
  modifier: PresenterModifier = PresenterModifier,
) {
  val rendering = renderWorkflow(
    workflow = workflow,
    props = props,
    onOutput = onOutput,
    config = config,
    parentSession = parentSession,
    renderKey = "",
  )
  Presenter(viewModel = rendering, modifier = modifier)
}
