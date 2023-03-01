package com.squareup.sample.stubvisibility

import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.internal.test.inAnyView
import leakcanary.DetectLeaksAfterTestSuccess
import org.hamcrest.CoreMatchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
internal class StubVisibilityAppTest {

  private val scenarioRule = ActivityScenarioRule(StubVisibilityActivity::class.java)

  @get:Rule val rules = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)

  @Test fun togglesFooter() {
    inAnyView(withId(R.id.should_be_wrapped))
      .check(matches(not(isDisplayed())))

    inAnyView(withText("Click to show footer"))
      .perform(click())

    inAnyView(withId(R.id.should_be_wrapped))
      .check(matches(isDisplayed()))

    inAnyView(withText("Click to hide footer"))
      .perform(click())

    inAnyView(withId(R.id.should_be_wrapped))
      .check(matches(not(isDisplayed())))
  }
}
