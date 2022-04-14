package com.squareup.benchmarks.performance.complex.poetry.views

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.container.panel.ScrimScreen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BodyAndModalsScreen

@OptIn(WorkflowUiExperimentalApi::class)
typealias MayBeLoadingScreen = BodyAndModalsScreen<ScrimScreen<OverviewDetailScreen>, LoaderSpinner>

@OptIn(WorkflowUiExperimentalApi::class)
fun MayBeLoadingScreen(
  baseScreen: OverviewDetailScreen,
  loaders: List<LoaderSpinner> = emptyList()
): MayBeLoadingScreen {
  return BodyAndModalsScreen(ScrimScreen(baseScreen, dimmed = loaders.isNotEmpty()), loaders)
}

@OptIn(WorkflowUiExperimentalApi::class)
val MayBeLoadingScreen.baseScreen: OverviewDetailScreen get() = body.content

@OptIn(WorkflowUiExperimentalApi::class)
val MayBeLoadingScreen.loaders: List<LoaderSpinner> get() = modals
