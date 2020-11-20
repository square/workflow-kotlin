package com.squareup.sample.hellobackbutton

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelloBackButtonEspressoTest {

  @Rule @JvmField val scenarioRule = ActivityScenarioRule(HelloBackButtonActivity::class.java)

  @Test fun wrappedTakesPrecedence() {
    onView(withId(R.id.hello_message)).apply {
      check(matches(withText("Able")))
      perform(click())
      check(matches(withText("Baker")))
      perform(click())
      check(matches(withText("Charlie")))
      pressBack()
      check(matches(withText("Baker")))
      pressBack()
      check(matches(withText("Able")))
    }
  }

  @Test fun outerHandlerAppliesIfWrappedHandlerIsNull() {
    onView(withId(R.id.hello_message)).apply {
      pressBack()
      onView(withText("Are you sure you want to do this thing?"))
          .check(matches(isDisplayed()))
    }
  }
}
