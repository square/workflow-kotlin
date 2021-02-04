package com.squareup.sample.stubvisibility

import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.internal.test.onWorkflowView
import org.hamcrest.CoreMatchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StubVisibilityAppTest {

  @Rule @JvmField val scenarioRule = ActivityScenarioRule(StubVisibilityActivity::class.java)

  @Test fun togglesFooter() {
    onWorkflowView(withId(R.id.should_be_wrapped))
        .check(matches(not(isDisplayed())))

    onWorkflowView(withText("Click to show footer"))
        .perform(click())

    onWorkflowView(withId(R.id.should_be_wrapped))
        .check(matches(isDisplayed()))

    onWorkflowView(withText("Click to hide footer"))
        .perform(click())

    onWorkflowView(withId(R.id.should_be_wrapped))
        .check(matches(not(isDisplayed())))
  }
}
