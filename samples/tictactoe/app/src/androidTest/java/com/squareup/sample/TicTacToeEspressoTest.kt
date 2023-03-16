package com.squareup.sample

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
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
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
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
@RunWith(AndroidJUnit4::class)
class TicTacToeEspressoTest {
  private val scenarioRule = ActivityScenarioRule(TicTacToeActivity::class.java)

  @get:Rule
  val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)

  private val scenario get() = scenarioRule.scenario

  @Before
  fun setUp() {
    scenario.onActivity { activity ->
      IdlingRegistry.getInstance().register(activity.idlingResource)
    }
  }

  @After
  fun tearDown() {
    scenario.onActivity { activity ->
      IdlingRegistry.getInstance().unregister(activity.idlingResource)
    }
  }

  @Test fun configChangeReflectsWorkflowState() {
    onView(withId(R.id.login_email)).inRoot(isDialog()).type("bad email")
    onView(withId(R.id.login_button)).inRoot(isDialog()).perform(click())

    onView(withId(R.id.login_error_message)).inRoot(isDialog())
      .check(matches(withText("Invalid address")))
    scenario.recreate()
    onView(withId(R.id.login_error_message)).inRoot(isDialog())
      .check(matches(withText("Invalid address")))
  }

  @Test fun editTextSurvivesConfigChange() {
    onView(withId(R.id.login_email)).inRoot(isDialog()).type("foo@bar")
    onView(withId(R.id.login_password)).inRoot(isDialog()).type("password")
    scenario.recreate()
    onView(withId(R.id.login_email)).inRoot(isDialog()).check(matches(withText("foo@bar")))
    // Don't save fields that shouldn't be.
    onView(withId(R.id.login_password)).inRoot(isDialog()).check(matches(withText("")))
  }

  @Test fun backStackPopRestoresViewState() {
    // The loading screen is pushed onto the back stack.
    onView(withId(R.id.login_email)).inRoot(isDialog()).type("foo@bar")
    onView(withId(R.id.login_password)).inRoot(isDialog()).type("bad password")
    onView(withId(R.id.login_button)).inRoot(isDialog()).perform(click())

    // Loading ends with an error, and we pop back to login. The
    // email should have been restored from view state.
    onView(withId(R.id.login_email)).inRoot(isDialog()).check(matches(withText("foo@bar")))
    onView(withId(R.id.login_error_message)).inRoot(isDialog())
      .check(matches(withText("Unknown email or invalid password")))
  }

  @Test fun dialogSurvivesConfigChange() {
    onView(withId(R.id.login_email)).inRoot(isDialog()).type("foo@bar")
    onView(withId(R.id.login_password)).inRoot(isDialog()).type("password")
    onView(withId(R.id.login_button)).inRoot(isDialog()).perform(click())

    onView(withId(R.id.player_X)).inRoot(isDialog()).type("Mister X")
    onView(withId(R.id.player_O)).inRoot(isDialog()).type("Sister O")
    onView(withId(R.id.start_game)).inRoot(isDialog()).perform(click())

    onGameView().perform(pressBack())

    onView(withText("Do you really want to concede the game?")).inRoot(isDialog())
      .check(matches(isDisplayed()))
    scenario.recreate()
    onView(withText("Do you really want to concede the game?")).inRoot(isDialog())
      .check(matches(isDisplayed()))
  }

  @Test fun canGoBackFromAlert() {
    onView(withId(R.id.login_email)).inRoot(isDialog()).type("foo@bar")
    onView(withId(R.id.login_password)).inRoot(isDialog()).type("password")
    onView(withId(R.id.login_button)).inRoot(isDialog()).perform(click())

    onView(withId(R.id.player_X)).inRoot(isDialog()).type("Mister X")
    onView(withId(R.id.player_O)).inRoot(isDialog()).type("Sister O")
    onView(withId(R.id.start_game)).inRoot(isDialog()).perform(click())

    onGameView().perform(pressBack())

    onView(withText("Do you really want to concede the game?")).inRoot(isDialog())
      .check(matches(isDisplayed()))
    onView(withText("I QUIT")).inRoot(isDialog()).perform(click())
    onView(withText("Really?")).inRoot(isDialog())
      .check(matches(isDisplayed()))
      .perform(pressBack())

    // Click a game cell to confirm the alert went away.
    clickCell(0)
  }

  @Test fun canGoBackInModalViewAndSeeRestoredViewState() {
    // Log in and hit the 2fa screen.
    onView(withId(R.id.login_email)).inRoot(isDialog()).type("foo@2fa")
    onView(withId(R.id.login_password)).inRoot(isDialog()).type("password")
    onView(withId(R.id.login_button)).inRoot(isDialog()).perform(click())

    // See 2nd factor, then use the back button to go back and see the login screen again.
    onView(withId(R.id.second_factor)).inRoot(isDialog())
      .check(matches(isDisplayed()))
      .perform(pressBack())

    // Make sure edit text was restored from view state cached by the back stack container.
    onView(withId(R.id.login_email)).inRoot(isDialog()).check(matches(withText("foo@2fa")))
  }

  @Test fun canGoBackInModalViewAfterConfigChangeAndSeeRestoredViewState() {
    // Log in and hit the 2fa screen.
    onView(withId(R.id.login_email)).inRoot(isDialog()).type("foo@2fa")
    onView(withId(R.id.login_password)).inRoot(isDialog()).type("password")
    onView(withId(R.id.login_button)).inRoot(isDialog()).perform(click())
    onView(withId(R.id.second_factor)).inRoot(isDialog()).check(matches(isDisplayed()))

    // Rotate and then use the back button to go back and see the login screen again.
    scenario.recreate()
    onView(withId(R.id.second_factor)).inRoot(isDialog()).perform(pressBack())

    // Make sure edit text was restored from view state cached by the back stack container.
    onView(withId(R.id.login_email)).inRoot(isDialog()).check(matches(withText("foo@2fa")))
  }

  /**
   * On tablets this revealed a problem with SavedStateRegistry.
   * https://github.com/square/workflow-kotlin/pull/656#issuecomment-1027274391
   */
  @Test fun fullJourney() {
    onView(withId(R.id.login_email)).inRoot(isDialog()).type("foo@bar")
    onView(withId(R.id.login_password)).inRoot(isDialog()).type("password")
    onView(withId(R.id.login_button)).inRoot(isDialog()).perform(click())

    onView(withId(R.id.start_game)).inRoot(isDialog()).perform(click())

    clickCell(0)
    clickCell(3)
    clickCell(1)
    clickCell(4)
    clickCell(2)

    onView(withText(R.string.exit)).perform(click())
    onView(withId(R.id.start_game)).inRoot(isDialog())
      .check(matches(isDisplayed()))
      .perform(pressBack())

    onView(withId(R.id.login_email)).inRoot(isDialog()).check(matches(isDisplayed()))
  }

  private fun onGameView(): ViewInteraction {
    return onView(withClassName(endsWith("GridLayout")))
  }

  private fun clickCell(index: Int) {
    onView(
      allOf(
        withParent(withClassName(endsWith("GridLayout"))),
        withParentIndex(index)
      )
    ).perform((click()))
  }

  private fun ViewInteraction.type(text: String) {
    perform(typeText(text), closeSoftKeyboard())
  }
}
