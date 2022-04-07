package com.squareup.benchmarks.performance.complex.poetry

import com.squareup.benchmarks.performance.complex.poetry.views.LoaderSpinner
import com.squareup.benchmarks.performance.complex.poetry.views.MayBeLoadingScreen
import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.asWorker
import com.squareup.workflow1.runningWorker
import kotlinx.coroutines.flow.Flow

typealias IsLoading = Boolean

class MaybeLoadingGatekeeperWorkflow<T : Any>(
  private val childWithLoading: Workflow<T, Any, OverviewDetailScreen>,
  private val childProps: T,
  private val isLoading: Flow<Boolean>
) : StatefulWorkflow<Unit, IsLoading, Unit, MayBeLoadingScreen>() {
  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): IsLoading = false

  override fun render(
    renderProps: Unit,
    renderState: IsLoading,
    context: RenderContext
  ): MayBeLoadingScreen {
    context.runningWorker(isLoading.asWorker()) {
      action {
        state = it
      }
    }
    return MayBeLoadingScreen(
      baseScreen = context.renderChild(childWithLoading, childProps) {
        action { setOutput(Unit) }
      },
      loaders = if (renderState) listOf(LoaderSpinner) else emptyList()
    )
  }

  override fun snapshotState(state: IsLoading): Snapshot? = null
}
