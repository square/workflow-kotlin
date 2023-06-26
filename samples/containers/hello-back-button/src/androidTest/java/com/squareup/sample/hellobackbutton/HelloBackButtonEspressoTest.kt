package com.squareup.sample.hellobackbutton

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
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
class HelloBackButtonEspressoTest {

  private val scenarioRule = ActivityScenarioRule(HelloBackButtonActivity::class.java)

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)

  @Test fun wrappedTakesPrecedence() {
    // The root workflow (AreYouSureWorkflow) wraps its child renderings
    // (instances of HelloBackButtonScreen) in its own BackButtonScreen,
    // which shows the "Are you sure" dialog.
    // That should only be in effect on the Able screen, which sets no backHandler of its
    // own. The Baker and Charlie screens set their own backHandlers,
    // which should take precedence over the root one. Thus, we should
    // be able to push to Charlie and pop all the way back to Able
    // without seeing the "Are you sure" dialog.
    onView(withId(R.id.hello_message)).apply {
      check(matches(withText("Able")))
      perform(click())
      check(matches(withText("Baker")))
      perform(click())
      check(matches(withText("Charlie")))
      perform(pressBack())
      check(matches(withText("Baker")))
      perform(pressBack())
      check(matches(withText("Able")))
    }
  }

  @Test fun outerHandlerAppliesIfWrappedHandlerIsNull() {
    onView(withId(R.id.hello_message)).apply {
      check(matches(isDisplayed()))
      perform(pressBack())
    }

    onView(withText("Are you sure you want to do this thing?"))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))
  }
}
