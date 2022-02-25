package com.squareup.sample.container.overviewdetail

import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth
import com.squareup.sample.container.R
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class OverviewDetailContainerTest {
  @get:Rule val scenarioRule = ActivityScenarioRule(ComponentActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  @Test fun throwsUsefulMessageOnNoChildren() {
    scenario.onActivity { activity ->
      val view = View(activity)

      val exception = assertFailsWith<IllegalStateException> {
        OverviewDetailContainer(view)
      }
      Truth.assertThat(exception.message).isEqualTo(
        "Layout must define only R.id.overview_detail_single_stub, " +
          "or else both R.id.overview_stub and R.id.detail_stub. " +
          "Found: overviewStub: null (parent null); detailStub: null (parent null); " +
          "singleStub: null (parent null)"
      )
    }
  }

  @Test fun throwsUsefulMessageOnTooManyChildren() {
    scenario.onActivity { activity ->
      val view = FrameLayout(activity).apply {
        addView(WorkflowViewStub(context).apply { id = R.id.overview_stub })
        addView(WorkflowViewStub(context).apply { id = R.id.detail_stub })
        addView(WorkflowViewStub(context).apply { id = R.id.overview_detail_single_stub })
      }

      val exception = assertFailsWith<IllegalStateException> {
        OverviewDetailContainer(view)
      }
      Truth.assertThat(exception.message).startsWith(
        "Layout must define only R.id.overview_detail_single_stub, " +
          "or else both R.id.overview_stub and R.id.detail_stub. "
      )
      Truth.assertThat(exception.message)
        .contains("overviewStub: com.squareup.workflow1.ui.WorkflowViewStub")
      Truth.assertThat(exception.message)
        .contains("app:id/overview_stub} (parent android.widget.FrameLayout")
      Truth.assertThat(exception.message)
        .contains("detailStub: com.squareup.workflow1.ui.WorkflowViewStub")
      Truth.assertThat(exception.message)
        .contains("app:id/detail_stub} (parent android.widget.FrameLayout")
      Truth.assertThat(exception.message)
        .contains("singleStub: com.squareup.workflow1.ui.WorkflowViewStub")
      Truth.assertThat(exception.message).contains(
        "app:id/overview_detail_single_stub} (parent android.widget.FrameLayout"
      )
    }
  }

  @Test fun throwsUsefulMessageOnMissingOverview() {
    scenario.onActivity { activity ->
      val view = FrameLayout(activity).apply {
        addView(WorkflowViewStub(context).apply { id = R.id.overview_stub })
      }

      val exception = assertFailsWith<IllegalStateException> {
        OverviewDetailContainer(view)
      }
      Truth.assertThat(exception.message).startsWith(
        "Layout must define only R.id.overview_detail_single_stub, " +
          "or else both R.id.overview_stub and R.id.detail_stub. "
      )
      Truth.assertThat(exception.message).endsWith(
        "detailStub: null (parent null); singleStub: null (parent null)"
      )

      Truth.assertThat(exception.message)
        .contains("overviewStub: com.squareup.workflow1.ui.WorkflowViewStub")
      Truth.assertThat(exception.message)
        .contains("app:id/overview_stub} (parent android.widget.FrameLayout")
    }
  }

  @Test fun throwsUsefulMessageOnMissingDetail() {
    scenario.onActivity { activity ->
      val view = FrameLayout(activity).apply {
        addView(WorkflowViewStub(context).apply { id = R.id.detail_stub })
      }

      val exception = assertFailsWith<IllegalStateException> {
        OverviewDetailContainer(view)
      }
      Truth.assertThat(exception.message).startsWith(
        "Layout must define only R.id.overview_detail_single_stub, " +
          "or else both R.id.overview_stub and R.id.detail_stub. " +
          "Found: overviewStub: null (parent null); "
      )
      Truth.assertThat(exception.message).endsWith(
        "singleStub: null (parent null)"
      )

      Truth.assertThat(exception.message)
        .contains("detailStub: com.squareup.workflow1.ui.WorkflowViewStub")
      Truth.assertThat(exception.message)
        .contains("app:id/detail_stub} (parent android.widget.FrameLayout")
    }
  }
}
