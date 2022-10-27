package com.squareup.sample.container.overviewdetail

import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import com.squareup.sample.container.R
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig.Detail
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig.Overview
import com.squareup.sample.container.overviewdetail.OverviewDetailConfig.Single
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.container.BackStackScreen

/**
 * Displays [OverviewDetailScreen] renderings in either split pane or single pane
 * treatment, depending on the setup of the given [View]. The view must provide
 * either a single [WorkflowViewStub] with id [R.id.overview_detail_single_stub],
 * or else two with ids [R.id.overview_stub] and [R.id.detail_stub].
 *
 * For single pane layouts, [OverviewDetailScreen] is repackaged as a [BackStackScreen]
 * with [OverviewDetailScreen.overviewRendering] as the base of the stack.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class OverviewDetailContainer(view: View) : ScreenViewRunner<OverviewDetailScreen> {

  private val overviewStub: WorkflowViewStub? = view.findViewById(R.id.overview_stub)
  private val detailStub: WorkflowViewStub? = view.findViewById(R.id.detail_stub)
  private val singleStub: WorkflowViewStub? = view.findViewById(R.id.overview_detail_single_stub)

  init {
    check((singleStub != null) xor (overviewStub != null && detailStub != null)) {
      "Layout must define only R.id.overview_detail_single_stub, " +
        "or else both R.id.overview_stub and R.id.detail_stub. " +
        "Found: overviewStub: $overviewStub (parent ${overviewStub?.parent}); " +
        "detailStub: $detailStub (parent ${detailStub?.parent}); " +
        "singleStub: $singleStub (parent ${singleStub?.parent})"
    }
  }

  override fun showRendering(
    rendering: OverviewDetailScreen,
    environment: ViewEnvironment
  ) {
    if (singleStub == null) {
      renderSplitView(rendering, environment)
    } else {
      renderSingleView(rendering, environment, singleStub)
    }
  }

  private fun renderSplitView(
    rendering: OverviewDetailScreen,
    viewEnvironment: ViewEnvironment
  ) {
    if (rendering.detailRendering == null && rendering.selectDefault != null) {
      rendering.selectDefault!!.invoke()
    } else {
      val overviewViewEnvironment = viewEnvironment + (OverviewDetailConfig to Overview)

      // Without this name, the two BackStackScreen containers will try
      // to sign up with SavedStateRegistry with the same id, and crash.
      val overviewRendering = NamedScreen(rendering.overviewRendering, "Overview")
      overviewStub!!.show(overviewRendering, overviewViewEnvironment)

      rendering.detailRendering
        ?.let { detail ->
          detailStub!!.actual.visibility = VISIBLE
          detailStub.show(
            detail,
            viewEnvironment + Detail
          )
        }
        ?: run {
          detailStub!!.actual.visibility = INVISIBLE
        }
    }
  }

  private fun renderSingleView(
    rendering: OverviewDetailScreen,
    viewEnvironment: ViewEnvironment,
    stub: WorkflowViewStub
  ) {
    val combined: BackStackScreen<*> = rendering.detailRendering
      ?.let { rendering.overviewRendering + it }
      ?: rendering.overviewRendering

    stub.show(combined, viewEnvironment + Single)
  }

  companion object : ScreenViewFactory<OverviewDetailScreen> by ScreenViewFactory.fromLayout(
    layoutId = R.layout.overview_detail,
    constructor = ::OverviewDetailContainer
  )
}
