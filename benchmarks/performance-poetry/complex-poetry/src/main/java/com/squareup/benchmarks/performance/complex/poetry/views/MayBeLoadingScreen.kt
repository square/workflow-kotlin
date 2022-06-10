package com.squareup.benchmarks.performance.complex.poetry.views

import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.container.panel.ScrimScreen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BodyAndModalsScreen
import com.squareup.workflow1.ui.container.ModalScreenOverlay

@OptIn(WorkflowUiExperimentalApi::class)
typealias MayBeLoadingScreen =
  BodyAndModalsScreen<ScrimScreen<OverviewDetailScreen>, ModalScreenOverlay<LoaderSpinner>>

@OptIn(WorkflowUiExperimentalApi::class)
fun MayBeLoadingScreen(
  baseScreen: OverviewDetailScreen,
  loaders: List<LoaderSpinner> = emptyList()
): MayBeLoadingScreen {
  return BodyAndModalsScreen(
    ScrimScreen(baseScreen, dimmed = loaders.isNotEmpty()),
    loaders.map { ModalScreenOverlay(it) }
  )
}

@OptIn(WorkflowUiExperimentalApi::class)
val MayBeLoadingScreen.baseScreen: OverviewDetailScreen
  get() = body.content

@OptIn(WorkflowUiExperimentalApi::class)
val MayBeLoadingScreen.loaders: List<LoaderSpinner>
  get() = overlays.map { it.content }
