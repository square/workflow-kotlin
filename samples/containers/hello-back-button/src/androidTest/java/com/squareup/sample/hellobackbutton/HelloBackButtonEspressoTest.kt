package com.squareup.sample.hellobackbutton

import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.inAnyView
import com.squareup.workflow1.ui.internal.test.actuallyPressBack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
class HelloBackButtonEspressoTest {

  @get:Rule val scenarioRule = ActivityScenarioRule(HelloBackButtonActivity::class.java)

  @Test fun wrappedTakesPrecedence() {
    inAnyView(withId(R.id.hello_message)).apply {
      check(matches(withText("Able")))
      perform(click())
      check(matches(withText("Baker")))
      perform(click())
      check(matches(withText("Charlie")))
      actuallyPressBack()
      check(matches(withText("Baker")))
      actuallyPressBack()
      check(matches(withText("Able")))
    }
  }

  @Test fun outerHandlerAppliesIfWrappedHandlerIsNull() {
    inAnyView(withId(R.id.hello_message)).apply {
      actuallyPressBack()
      inAnyView(withText("Are you sure you want to do this thing?"))
        .check(matches(isDisplayed()))
    }
  }
}
