package com.squareup.workflow1.ui.internal.test

import android.view.View
import androidx.test.espresso.Espresso
import androidx.test.espresso.Root
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.any
import org.hamcrest.TypeSafeMatcher

/**
 * Fork of [Espresso.onView] that looks in all [Root]s, not just the one matched by the default
 * root matcher. The default root matcher will sometimes fail to find views in our dialogs.
 */
public fun onWorkflowView(viewMatcher: Matcher<View>): ViewInteraction {
  return Espresso.onView(viewMatcher).inRoot(any(Root::class.java))
}

/**
 * Fork of [Espresso.pressBack] that finds the root view of the focused window only, instead of
 * using the default root matcher. This is necessary because when we are showing dialogs,
 * the default matcher will sometimes match the wrong window, and the back press won't do
 * anything.
 */
public fun workflowPressBack() {
  val rootHasFocusMatcher = object : TypeSafeMatcher<Root>() {
    override fun describeTo(description: Description) {
      description.appendText("has window focus")
    }

    override fun matchesSafely(item: Root): Boolean {
      return item.decorView.hasWindowFocus()
    }
  }

  Espresso.onView(allOf(isRoot(), isDisplayed()))
    .inRoot(rootHasFocusMatcher)
    .perform(ViewActions.pressBack())
}
