package com.squareup.sample

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withParentIndex
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.sample.mainactivity.TicTacToeActivity
import com.squareup.sample.tictactoe.R
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.internal.test.actuallyPressBack
import com.squareup.workflow1.ui.internal.test.inAnyView
import com.squareup.workflow1.ui.internal.test.retryBlocking
import leakcanary.DetectLeaksAfterTestSuccess
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.endsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * This app is our most complex sample, which makes it a great candidate for
 * integration testing — especially of modals, back stacks, back button handling,
 * and view state management.
 */
@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
class TicTacToeEspressoTest {

  private val scenarioRule = ActivityScenarioRule(TicTacToeActivity::class.java)

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)
  private val scenario get() = scenarioRule.scenario

  @Before
  fun setUp() {
    scenario.onActivity { activity ->
      IdlingRegistry.getInstance()
        .register(activity.idlingResource)
      activity.requestedOrientation = SCREEN_ORIENTATION_PORTRAIT
    }
  }

  @After
  fun tearDown() {
    scenario.onActivity { activity ->
      IdlingRegistry.getInstance()
        .unregister(activity.idlingResource)
    }
  }

  @Test fun configChangeReflectsWorkflowState() = retryBlocking {
    inAnyView(withId(R.id.login_email)).type("bad email")
    inAnyView(withId(R.id.login_button)).perform(click())

    inAnyView(withId(R.id.login_error_message)).check(matches(withText("Invalid address")))
    rotate()
    inAnyView(withId(R.id.login_error_message)).check(matches(withText("Invalid address")))
  }

  @Test fun editTextSurvivesConfigChange() = retryBlocking {
    inAnyView(withId(R.id.login_email)).type("foo@bar")
    inAnyView(withId(R.id.login_password)).type("password")
    rotate()
    inAnyView(withId(R.id.login_email)).check(matches(withText("foo@bar")))
    // Don't save fields that shouldn't be.
    inAnyView(withId(R.id.login_password)).check(matches(withText("")))
  }

  @Test fun backStackPopRestoresViewState() = retryBlocking {
    // The loading screen is pushed onto the back stack.
    inAnyView(withId(R.id.login_email)).type("foo@bar")
    inAnyView(withId(R.id.login_password)).type("bad password")
    inAnyView(withId(R.id.login_button)).perform(click())

    // Loading ends with an error, and we pop back to login. The
    // email should have been restored from view state.
    inAnyView(withId(R.id.login_email)).check(matches(withText("foo@bar")))
    inAnyView(withId(R.id.login_error_message))
      .check(matches(withText("Unknown email or invalid password")))
  }

  @Test fun dialogSurvivesConfigChange() = retryBlocking {
    inAnyView(withId(R.id.login_email)).type("foo@bar")
    inAnyView(withId(R.id.login_password)).type("password")
    inAnyView(withId(R.id.login_button)).perform(click())

    inAnyView(withId(R.id.player_X)).type("Mister X")
    inAnyView(withId(R.id.player_O)).type("Sister O")
    inAnyView(withId(R.id.start_game)).perform(click())

    actuallyPressBack()
    inAnyView(withText("Do you really want to concede the game?"))
      .check(matches(isDisplayed()))
    rotate()
    inAnyView(withText("Do you really want to concede the game?"))
      .check(matches(isDisplayed()))
  }

  @Test fun canGoBackFromAlert() = retryBlocking {
    inAnyView(withId(R.id.login_email)).type("foo@bar")
    inAnyView(withId(R.id.login_password)).type("password")
    inAnyView(withId(R.id.login_button)).perform(click())

    inAnyView(withId(R.id.player_X)).type("Mister X")
    inAnyView(withId(R.id.player_O)).type("Sister O")
    inAnyView(withId(R.id.start_game)).perform(click())

    actuallyPressBack()
    inAnyView(withText("Do you really want to concede the game?"))
      .check(matches(isDisplayed()))
    inAnyView(withText("I QUIT")).perform(click())
    inAnyView(withText("Really?"))
      .check(matches(isDisplayed()))

    actuallyPressBack()
    // Click a game cell to confirm the alert went away.
    clickCell(0)
  }

  @Test fun canGoBackInModalViewAndSeeRestoredViewState() = retryBlocking {
    // Log in and hit the 2fa screen.
    inAnyView(withId(R.id.login_email)).type("foo@2fa")
    inAnyView(withId(R.id.login_password)).type("password")
    inAnyView(withId(R.id.login_button)).perform(click())
    inAnyView(withId(R.id.second_factor)).check(matches(isDisplayed()))

    // Use the back button to go back and see the login screen again.
    actuallyPressBack()
    // Make sure edit text was restored from view state cached by the back stack container.
    inAnyView(withId(R.id.login_email)).check(matches(withText("foo@2fa")))
  }

  @Test fun canGoBackInModalViewAfterConfigChangeAndSeeRestoredViewState() = retryBlocking {
    // Log in and hit the 2fa screen.
    inAnyView(withId(R.id.login_email)).type("foo@2fa")
    inAnyView(withId(R.id.login_password)).type("password")
    inAnyView(withId(R.id.login_button)).perform(click())
    inAnyView(withId(R.id.second_factor)).check(matches(isDisplayed()))

    // Rotate and then use the back button to go back and see the login screen again.
    rotate()
    actuallyPressBack()
    // Make sure edit text was restored from view state cached by the back stack container.
    inAnyView(withId(R.id.login_email)).check(matches(withText("foo@2fa")))
  }

  /**
   * On tablets this revealed a problem with SavedStateRegistry.
   * https://github.com/square/workflow-kotlin/pull/656#issuecomment-1027274391
   */
  @Test fun fullJourney() = retryBlocking {
    inAnyView(withId(R.id.login_email)).type("foo@bar")
    inAnyView(withId(R.id.login_password)).type("password")
    inAnyView(withId(R.id.login_button)).perform(click())

    inAnyView(withId(R.id.start_game)).perform(click())

    clickCell(0)
    clickCell(3)
    clickCell(1)
    clickCell(4)
    clickCell(2)

    inAnyView(withText(R.string.exit)).perform(click())
    inAnyView(withId(R.id.start_game)).check(matches(isDisplayed()))
    actuallyPressBack()
    inAnyView(withId(R.id.login_email)).check(matches(isDisplayed()))
  }

  private fun clickCell(index: Int) {
    inAnyView(
      allOf(
        withParent(withClassName(endsWith("GridLayout"))),
        withParentIndex(index)
      )
    ).perform((click()))
  }

  private fun ViewInteraction.type(text: String) {
    perform(typeText(text), closeSoftKeyboard())
  }

  private fun rotate() {
    scenario.onActivity {
      it.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE
    }
  }
}
