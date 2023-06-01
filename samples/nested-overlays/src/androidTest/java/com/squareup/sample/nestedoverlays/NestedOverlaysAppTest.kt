package com.squareup.sample.nestedoverlays

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
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
    onView(withText("❌")).perform(click())
    onTopCoverEverything().perform(click())
    onView(withText("❌")).perform(click())

    onView(withText("Hide Top")).perform(click())
    onTopCoverBody().assertNotDisplayed()
    onTopCoverEverything().assertNotDisplayed()
    onBottomCoverBody().assertDisplayed()
    onBottomCoverEverything().assertDisplayed()

    onView(withText("Hide Bottom")).perform(click())
    onTopCoverBody().assertNotDisplayed()
    onTopCoverEverything().assertNotDisplayed()
    onBottomCoverBody().assertNotDisplayed()
    onBottomCoverEverything().assertNotDisplayed()
  }

  // https://github.com/square/workflow-kotlin/issues/966
  @Test fun canInsertDialog() {
    onTopCoverEverything().perform(click())

    // Cannot see the inner dialog.
    onView(withText("Hide Top")).inRoot(isDialog()).check(doesNotExist())

    // Click the outer dialog's button to show the inner dialog.
    onView(withText("Cover Body")).inRoot(isDialog()).perform(click())
    // Inner was created below outer, so we still can't see it.
    onView(withText("Hide Top")).inRoot(isDialog()).check(doesNotExist())

    // Close the outer dialog.
    onView(withText("❌")).inRoot(isDialog()).perform(click())
    // Now we can see the inner.
    onView(withText("Hide Top")).inRoot(isDialog()).check(matches(isDisplayed()))
    // Close it to confirm it really works.
    onView(withText("❌")).inRoot(isDialog()).perform(click())
    onTopCoverEverything().check(matches(isDisplayed()))
  }

  @Test fun canInsertAndRemoveCoveredDialog() {
    // Show the outer dialog
    onTopCoverEverything().perform(click())
    // Show the inner dialog behind it
    onView(withText("Cover Body")).inRoot(isDialog()).perform(click())
    // Close the (covered) inner dialog and don't crash. :/
    onView(withText("Reveal Body")).inRoot(isDialog()).perform(click())
    // Close the outer dialog
    onView(withText("❌")).inRoot(isDialog()).perform(click())
    // We can see the activity window again
    onTopCoverEverything().check(matches(isDisplayed()))
  }

  @Test fun whenReorderingViewStateIsPreserved() {
    // Show the outer dialog
    onTopCoverEverything().perform(click())

    // Type something on it
    onView(withId(R.id.button_bar_text)).inRoot(isDialog())
      .perform(typeText("banana"))

    // Click the outer dialog's button to show the inner dialog.
    onView(withText("Cover Body")).inRoot(isDialog()).perform(click())

    // The original outer dialog was destroyed and replaced.
    // Check that the text we entered made it to the replacement dialog via view state.
    onView(withId(R.id.button_bar_text)).inRoot(isDialog())
      .check(matches(withText("banana")))
  }

  @Test fun orderPreservedOnConfigChange() {
    // Show the outer dialog
    onTopCoverEverything().perform(click())

    // Click the outer dialog's button to show the inner dialog.
    onView(withText("Cover Body")).inRoot(isDialog()).perform(click())

    // "Config change"
    scenarioRule.scenario.recreate()

    // The green "Cover Everything" dialog is on top.
    onView(withText("Reveal Body")).inRoot(isDialog()).check(matches(isDisplayed()))
  }

  // https://github.com/square/workflow-kotlin/issues/314
  @Test fun whenBodyAndOverlaysStopsBeingRenderedDialogsAreDismissed() {
    onBottomCoverBody().perform(click())
    onView(withText("💣")).inRoot(isDialog()).perform(click())

    onBottomCoverBody().check(doesNotExist())
    onView(withText("Reset")).perform(click())

    onBottomCoverBody().perform(click())
    onView(withText("💣")).inRoot(isDialog()).check(matches(isDisplayed()))
  }

  // So far can't express this in Espresso, because it refuses to work
  // w/a root that lacks window focus. Considering move to Maestro.
  // In the meantime I'd like to keep this commented out block around
  // as a reminder.

  // @Test fun canCoverDialogAndRemoveItWhileCovered() {
  //   // Show the inner dialog
  //   onTopCoverBody().perform(click())
  //
  //   lateinit var activity: Activity
  //   scenarioRule.scenario.onActivity { activity = it }
  //
  //   // Show the outer dialog
  //   onTopCoverEverything()
  //     .inRoot(
  //       allOf(
  //         withDecorView(Matchers.`is`(activity.window.decorView)),
  //         Matchers.not(hasWindowFocus())
  //       )
  //     )
  //     .perform(click())
  //
  //   // Close the (covered) inner dialog
  //   onView(withText("Reveal Body")).inRoot(isDialog()).perform(click())
  //   // Close the outer dialog
  //   onView(withText("❌")).inRoot(isDialog()).perform(click())
  //   // We can see the activity window again
  //   onTopCoverEverything().check(matches(isDisplayed()))
  // }
  //
  // /**
  //  * Like the private (why?) `hasWindowFocus` method in Espresso, but
  //  * built into a `Matcher<Root>` rather than a `Matcher<View>` (since
  //  * that was our only use case).
  //  */
  // fun hasWindowFocus(): Matcher<Root> {
  //   return withDecorView(object : TypeSafeMatcher<View>() {
  //     override fun describeTo(description: Description) {
  //       description.appendText("has window focus (Square fork)")
  //     }
  //
  //     override fun matchesSafely(item: View): Boolean = item.hasWindowFocus()
  //   })
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
