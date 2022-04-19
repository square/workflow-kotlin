package com.squareup.sample.poetryapp

import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.sample.container.poetryapp.R
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.internal.test.inAnyView
import com.squareup.workflow1.ui.internal.test.wrapInLeakCanary
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
class PoetryAppTest {

  private val scenarioRule = ActivityScenarioRule(PoetryActivity::class.java)
  @get:Rule val rules = RuleChain.outerRule(scenarioRule)
    .around(IdlingDispatcherRule)
    .wrapInLeakCanary()

  @Test fun launches() {
    inAnyView(withText(R.string.poems))
      .check(matches(isDisplayed()))
  }
}
