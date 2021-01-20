package com.squareup.workflow1.ui.backstack.test

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.test.fixtures.BackStackTestActivity
import com.squareup.workflow1.ui.backstack.test.fixtures.BackStackTestActivity.TestRendering
import com.squareup.workflow1.ui.backstack.toBackStackScreen
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
class BackStackContainerTest {

  @Rule @JvmField val scenarioRule = ActivityScenarioRule(BackStackTestActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  @Test fun restores_view_on_pop_without_config_change() {
    lateinit var firstScreen: TestRendering
    lateinit var firstView: View

    scenario.onActivity { activity ->
      // Set some view state to be saved and restored.
      activity.currentTestView.viewState = "hello world"
      firstView = activity.currentTestView

      // Navigate to another screen.
      firstScreen = activity.backstack!!.top
      activity.backstack = listOf(firstScreen, TestRendering("new screen")).toBackStackScreen()
    }
    onView(withTagValue(equalTo("new screen"))).check(matches(isCompletelyDisplayed()))
    scenario.onActivity { activity ->
      assertThat(activity.currentTestView.viewState).isEqualTo("")

      // Navigate back.
      activity.backstack = listOf(firstScreen).toBackStackScreen()
    }

    // Wait for transition to finish.
    onView(withTagValue(equalTo("initial"))).check(matches(isCompletelyDisplayed()))

    scenario.onActivity { activity ->
      // Ensure that the view instance wasn't re-used.
      assertThat(activity.currentTestView).isNotSameInstanceAs(firstView)

      // Check that the view state was actually restored.
      assertThat(activity.currentTestView.viewState).isEqualTo("hello world")
    }
  }

  @Test fun restores_view_after_config_change() {
    scenario.onActivity { activity ->
      // Set some view state to be saved and restored.
      activity.currentTestView.viewState = "hello world"
    }

    // Destroy and recreate the activity.
    scenario.recreate()

    scenario.onActivity { activity ->
      // Check that the view state was actually restored.
      assertThat(activity.currentTestView.viewState).isEqualTo("hello world")
    }
  }

  @Test fun restores_view_on_pop_after_config_change() {
    lateinit var firstScreen: TestRendering

    scenario.onActivity { activity ->
      // Set some view state to be saved and restored.
      activity.currentTestView.viewState = "hello world"

      // Navigate to another screen.
      firstScreen = activity.backstack!!.top
      activity.backstack = listOf(firstScreen, TestRendering("new screen")).toBackStackScreen()
    }
    onView(withTagValue(equalTo("new screen"))).check(matches(isCompletelyDisplayed()))
    scenario.onActivity { activity ->
      assertThat(activity.currentTestView.viewState).isEqualTo("")
    }

    // Destroy and recreate the activity.
    scenario.recreate()

    scenario.onActivity { activity ->
      // Navigate back.
      activity.backstack = listOf(firstScreen).toBackStackScreen()
    }

    // Wait for transition to finish.
    onView(withTagValue(equalTo("initial"))).check(matches(isCompletelyDisplayed()))

    scenario.onActivity { activity ->
      // Check that the view state was actually restored.
      assertThat(activity.currentTestView.viewState).isEqualTo("hello world")
    }
  }
}
