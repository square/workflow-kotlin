package com.squareup.sample.helloworkflowfragment

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelloWorkflowFragmentAppTest {

  @Rule @JvmField val scenarioRule = ActivityScenarioRule(HelloWorkflowFragmentActivity::class.java)

  @Test fun togglesHelloAndGoodbye() {
    onView(withText(containsString("Hello")))
      .check(matches(isDisplayed()))
      .perform(click())

    onView(withText(containsString("Goodbye")))
      .check(matches(isDisplayed()))
      .perform(click())

    onView(withText(containsString("Hello")))
      .check(matches(isDisplayed()))
  }
}
