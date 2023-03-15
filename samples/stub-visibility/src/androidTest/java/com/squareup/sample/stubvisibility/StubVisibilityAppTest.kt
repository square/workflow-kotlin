package com.squareup.sample.stubvisibility

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.hamcrest.CoreMatchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class StubVisibilityAppTest {

  private val scenarioRule = ActivityScenarioRule(StubVisibilityActivity::class.java)

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)

  @Test fun togglesFooter() {
    onView(withId(R.id.should_be_wrapped))
      .check(matches(not(isDisplayed())))

    onView(withText("Click to show footer"))
      .perform(click())

    onView(withId(R.id.should_be_wrapped))
      .check(matches(isDisplayed()))

    onView(withText("Click to hide footer"))
      .perform(click())

    onView(withId(R.id.should_be_wrapped))
      .check(matches(not(isDisplayed())))
  }
}
