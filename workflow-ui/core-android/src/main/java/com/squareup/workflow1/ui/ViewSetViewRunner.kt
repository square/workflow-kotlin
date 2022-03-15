package com.squareup.workflow1.ui

import android.view.View

/**
 * Puts the given [ScreenViewRunner] in a tag on the receiving view. Expected to be
 * called immediately after the view is built, and never again.
 *
 * @throws IllegalStateException if the runner was already set.
 */
@WorkflowUiExperimentalApi
@PublishedApi
internal fun <ScreenT : Screen> View.setViewRunner(runner: ScreenViewRunner<ScreenT>) {
  getTag(R.id.workflow_view_runner)?.let {
    error(
      "Updating a view's ScreenViewRunner is not supported, " +
        "but found existing runner $it on view $this"
    )
  }
  setTag(R.id.workflow_view_runner, runner)
}

@WorkflowUiExperimentalApi
@PublishedApi
internal fun <ScreenT : Screen> View.getViewRunner(): ScreenViewRunner<ScreenT> {
  @Suppress("UNCHECKED_CAST")
  return getTag(R.id.workflow_view_runner) as? ScreenViewRunner<ScreenT>
    ?: error(
      "Expected a ScreenViewRunner on $this, instead found ${getTag(R.id.workflow_view_runner)}"
    )
}
