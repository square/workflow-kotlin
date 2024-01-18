package com.squareup.benchmarks.performance.complex.poetry.views

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.container.panel.ScrimScreen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.navigation.FullScreenModal

@OptIn(WorkflowUiExperimentalApi::class)
typealias MayBeLoadingScreen =
  BodyAndOverlaysScreen<ScrimScreen<OverviewDetailScreen<*>>, FullScreenModal<LoaderSpinner>>

@OptIn(WorkflowUiExperimentalApi::class)
fun MayBeLoadingScreen(
  baseScreen: OverviewDetailScreen<*>,
  loaders: List<LoaderSpinner> = emptyList()
): MayBeLoadingScreen {
  return BodyAndOverlaysScreen(
    ScrimScreen(baseScreen, dimmed = loaders.isNotEmpty()),
    loaders.map { FullScreenModal(it) }
  )
}
