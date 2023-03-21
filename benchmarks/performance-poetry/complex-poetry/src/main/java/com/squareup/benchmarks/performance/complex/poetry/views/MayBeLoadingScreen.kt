package com.squareup.benchmarks.performance.complex.poetry.views

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.container.panel.ScrimScreen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.container.FullScreenOverlay

@OptIn(WorkflowUiExperimentalApi::class)
typealias MayBeLoadingScreen =
  BodyAndOverlaysScreen<ScrimScreen<OverviewDetailScreen>, FullScreenOverlay<LoaderSpinner>>

@OptIn(WorkflowUiExperimentalApi::class)
fun MayBeLoadingScreen(
  baseScreen: OverviewDetailScreen,
  loaders: List<LoaderSpinner> = emptyList()
): MayBeLoadingScreen {
  return BodyAndOverlaysScreen(
    ScrimScreen(baseScreen, dimmed = loaders.isNotEmpty()),
    loaders.map { FullScreenOverlay(it) }
  )
}
