package com.squareup.sample.helloworkflow

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelloWorkflowAppTest {

  private val scenarioRule = ActivityScenarioRule(HelloWorkflowActivity::class.java)

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)

  @Test fun togglesHelloAndGoodbye() {
    onView(withText("Hello"))
      .check(matches(isDisplayed()))
      .perform(click())

    onView(withText("Goodbye"))
      .check(matches(isDisplayed()))
      .perform(click())

    onView(withText("Hello"))
      .check(matches(isDisplayed()))
  }
}
