package com.squareup.sample.helloworkflowfragment

import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.internal.test.inAnyView
import leakcanary.DetectLeaksAfterTestSuccess
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
class HelloWorkflowFragmentAppTest {

  private val scenarioRule = ActivityScenarioRule(HelloWorkflowFragmentActivity::class.java)

  @get:Rule val rules = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)

  @Test fun togglesHelloAndGoodbye() {
    inAnyView(withText(containsString("Hello")))
      .check(matches(isDisplayed()))
      .perform(click())

    inAnyView(withText(containsString("Goodbye")))
      .check(matches(isDisplayed()))
      .perform(click())

    inAnyView(withText(containsString("Hello")))
      .check(matches(isDisplayed()))
  }
}
