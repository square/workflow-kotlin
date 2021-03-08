package com.squareup.sample.ravenapp

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RavenAppTest {

  @Rule @JvmField val scenarioRule = ActivityScenarioRule(RavenActivity::class.java)

  @Test fun launches() {
    onView(withText("The Raven"))
        .check(matches(isDisplayed()))
  }
}
