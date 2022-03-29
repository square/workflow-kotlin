package com.squareup.benchmarks.performance.complex.poetry.views

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.container.panel.ScrimContainerScreen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.modal.HasModals

@OptIn(WorkflowUiExperimentalApi::class)
class MayBeLoadingScreen(
  private val baseScreen: OverviewDetailScreen,
  private val loaders: List<LoaderSpinner> = emptyList()
) : HasModals<ScrimContainerScreen<OverviewDetailScreen>, LoaderSpinner> {
  override val beneathModals: ScrimContainerScreen<OverviewDetailScreen>
    get() = ScrimContainerScreen(
      wrapped = baseScreen,
      dimmed = loaders.isNotEmpty()
    )
  override val modals: List<LoaderSpinner>
    get() = loaders
}
