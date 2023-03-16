package com.squareup.sample.nestedoverlays

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withParentIndex
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.hamcrest.core.AllOf.allOf
import org.hamcrest.core.IsNot.not
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NestedOverlaysAppTest {

  private val scenarioRule = ActivityScenarioRule(NestedOverlaysActivity::class.java)

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)

  @Test fun basics() {
    onTopCoverBody().assertDisplayed()
    onTopCoverEverything().assertDisplayed()
    onBottomCoverBody().assertDisplayed()
    onBottomCoverEverything().assertDisplayed()

    onTopCoverBody().perform(click())
    onView(withText("Close")).perform(click())
    onTopCoverEverything().perform(click())
    onView(withText("Close")).perform(click())

    onView(withText("Hide Top Bar")).perform(click())
    onTopCoverBody().assertNotDisplayed()
    onTopCoverEverything().assertNotDisplayed()
    onBottomCoverBody().assertDisplayed()
    onBottomCoverEverything().assertDisplayed()

    onView(withText("Hide Bottom Bar")).perform(click())
    onTopCoverBody().assertNotDisplayed()
    onTopCoverEverything().assertNotDisplayed()
    onBottomCoverBody().assertNotDisplayed()
    onBottomCoverEverything().assertNotDisplayed()
  }

  // https://github.com/square/workflow-kotlin/issues/966
  @Test fun canInsertDialog() {
    onTopCoverEverything().perform(click())
    onView(withText("Hide Top Bar")).check(doesNotExist())
    onView(withText("Cover Body")).perform(click())

    // This line fails due to https://github.com/square/workflow-kotlin/issues/966
    // onView(withText("Hide Top Bar")).check(doesNotExist())

    // Should continue to close the top sheet and assert that the inner sheet is visible.
  }

  // So far can't express this in Espresso. Considering move to Maestro
  // @Test fun canClickPastInnerWindow() {
  //   onView(allOf(withText("Cover Everything"), withParent(withParentIndex(0))))
  //     .perform(click())
  //
  //   scenario.onActivity { activity ->
  //     onView(allOf(withText("Cover Everything"), withParent(withParentIndex(0))))
  //       .inRoot(withDecorView(not(`is`(activity.window.decorView))))
  //       .perform(click())
  //   }
  // }

  private fun ViewInteraction.assertNotDisplayed() {
    check(matches(not(isDisplayed())))
  }

  private fun ViewInteraction.assertDisplayed() {
    check(matches(isDisplayed()))
  }

  private fun onBottomCoverEverything() =
    onView(allOf(withText("Cover Everything"), withParent(withParentIndex(2))))

  private fun onBottomCoverBody() =
    onView(allOf(withText("Cover Body"), withParent(withParentIndex(2))))

  private fun onTopCoverBody() =
    onView(allOf(withText("Cover Body"), withParent(withParentIndex(0))))

  private fun onTopCoverEverything() =
    onView(allOf(withText("Cover Everything"), withParent(withParentIndex(0))))
}
