package com.squareup.sample.helloworkflow

import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.internal.test.inAnyView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelloWorkflowAppTest {

  @Rule @JvmField val scenarioRule = ActivityScenarioRule(HelloWorkflowActivity::class.java)

  @Test fun togglesHelloAndGoodbye() {
    inAnyView(withText("Hello"))
        .check(matches(isDisplayed()))
        .perform(click())

    inAnyView(withText("Goodbye"))
        .check(matches(isDisplayed()))
        .perform(click())

    inAnyView(withText("Hello"))
        .check(matches(isDisplayed()))
  }
}
