package com.squareup.workflow1.ui.navigation

import android.view.View
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@OptIn(WorkflowUiExperimentalApi::class)
internal class ViewBackHandlerTest {
  private val scenarioRule = ActivityScenarioRule(ComponentActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)

  private var viewHandlerCount = 0
  private fun viewBackHandler() {
    viewHandlerCount++
  }

  @Test fun itWorksWhenHandlerIsAddedBeforeAttach() {
    scenario.onActivity { activity ->
      val view = View(activity)
      WorkflowLifecycleOwner.installOn(view, activity)
      view.setBackHandler { viewBackHandler() }

      activity.setContentView(view)
      assertThat(viewHandlerCount).isEqualTo(0)

      activity.onBackPressedDispatcher.onBackPressed()
      assertThat(viewHandlerCount).isEqualTo(1)
    }
  }

  @Test fun itWorksWhenHandlerIsAddedAfterAttach() {
    scenario.onActivity { activity ->
      val view = View(activity)
      activity.setContentView(view)

      view.setBackHandler { viewBackHandler() }
      assertThat(viewHandlerCount).isEqualTo(0)

      activity.onBackPressedDispatcher.onBackPressed()
      assertThat(viewHandlerCount).isEqualTo(1)
    }
  }
}
