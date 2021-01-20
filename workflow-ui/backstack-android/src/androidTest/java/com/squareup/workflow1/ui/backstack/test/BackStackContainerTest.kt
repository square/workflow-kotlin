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
import com.squareup.workflow1.ui.backstack.test.fixtures.ViewStateTestView
import com.squareup.workflow1.ui.backstack.test.fixtures.ViewStateTestView.ViewHooks
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
      firstScreen = activity.backstack!!.top
    }

    // Navigate to another screen.
    setBackstack(firstScreen, TestRendering("new screen"))
    assertThat(viewForScreen("new screen").viewState).isEqualTo("")

    // Navigate back.
    setBackstack(firstScreen)

    viewForScreen("initial").let {
      // Ensure that the view instance wasn't re-used.
      assertThat(it).isNotSameInstanceAs(firstView)

      // Check that the view state was actually restored.
      assertThat(it.viewState).isEqualTo("hello world")
    }
  }

  @Test fun restores_view_after_config_change() {
    scenario.onActivity { activity ->
      // Set some view state to be saved and restored.
      activity.currentTestView.viewState = "hello world"
    }

    // Destroy and recreate the activity.
    scenario.recreate()

    // Check that the view state was actually restored.
    assertThat(viewForScreen("initial").viewState).isEqualTo("hello world")
  }

  @Test fun restores_view_on_pop_after_config_change() {
    lateinit var firstScreen: TestRendering

    scenario.onActivity { activity ->
      // Set some view state to be saved and restored.
      activity.currentTestView.viewState = "hello world"
      firstScreen = activity.backstack!!.top
    }

    // Navigate to another screen.
    setBackstack(firstScreen, TestRendering("new screen"))
    assertThat(viewForScreen("new screen").viewState).isEqualTo("")

    // Destroy and recreate the activity.
    scenario.recreate()

    // Navigate back.
    setBackstack(firstScreen)

    // Check that the view state was actually restored.
    assertThat(viewForScreen("initial").viewState).isEqualTo("hello world")
  }

  @Test fun state_rendering_and_attach_ordering() {
    val events = mutableListOf<String>()
    fun log(name: String, event: String, view: ViewStateTestView) {
      events += "$name $event viewState=${view.viewState}"
    }

    fun consumeEvents() = events.toList().also { events.clear() }

    fun testRendering(name: String) = TestRendering(
      name = name,
      onViewCreated = { view -> log(name, "onViewCreated", view) },
      onShowRendering = { view -> log(name, "onShowRendering", view) },
      viewHooks = object : ViewHooks {
        override fun onSaveInstanceState(view: ViewStateTestView) = log(name, "onSave", view)
        override fun onRestoreInstanceState(view: ViewStateTestView) = log(name, "onRestore", view)
        override fun onAttach(view: ViewStateTestView) = log(name, "onAttach", view)
        override fun onDetach(view: ViewStateTestView) = log(name, "onDetach", view)
      }
    )

    val firstRendering = testRendering("first")
    val secondRendering = testRendering("second")

    // Setup initial screen.
    setBackstack(firstRendering)
    waitForScreen(firstRendering.name)
    scenario.onActivity {
      it.currentTestView.viewState = "hello"
    }
    assertThat(consumeEvents()).containsExactly(
      "first onViewCreated viewState=",
      "first onShowRendering viewState=",
      "first onAttach viewState="
    )

    // Navigate forward.
    setBackstack(firstRendering, secondRendering)
    waitForScreen(secondRendering.name)
    assertThat(consumeEvents()).containsExactly(
      "second onViewCreated viewState=",
      "second onShowRendering viewState=",
      "first onSave viewState=hello",
      "first onDetach viewState=hello",
      "second onAttach viewState="
    )

    // Navigate back.
    setBackstack(firstRendering)
    waitForScreen(firstRendering.name)
    assertThat(consumeEvents()).containsExactly(
      "first onViewCreated viewState=",
      "first onShowRendering viewState=",
      "first onRestore viewState=hello",
      "second onDetach viewState=",
      "first onAttach viewState=hello"
    )
  }

  private fun setBackstack(vararg renderings: TestRendering) {
    scenario.onActivity {
      it.backstack = renderings.asList().toBackStackScreen()
    }
  }

  private fun viewForScreen(name: String): ViewStateTestView {
    waitForScreen(name)
    lateinit var view: ViewStateTestView
    scenario.onActivity {
      view = it.currentTestView
    }
    return view
  }

  private fun waitForScreen(name: String) {
    onView(withTagValue(equalTo(name))).check(matches(isCompletelyDisplayed()))
  }
}
