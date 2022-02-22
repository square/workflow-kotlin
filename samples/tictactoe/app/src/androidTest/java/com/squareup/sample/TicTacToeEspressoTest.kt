package com.squareup.sample

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.view.View
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withParentIndex
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.sample.gameworkflow.GamePlayScreen
import com.squareup.sample.gameworkflow.Player
import com.squareup.sample.gameworkflow.symbol
import com.squareup.sample.mainactivity.TicTacToeActivity
import com.squareup.sample.tictactoe.R
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.environment
import com.squareup.workflow1.ui.getRendering
import com.squareup.workflow1.ui.internal.test.DetectLeaksAfterTestSuccess
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.internal.test.actuallyPressBack
import com.squareup.workflow1.ui.internal.test.inAnyView
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.endsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * This app is our most complex sample, which makes it a great candidate for
 * integration testing — especially of modals, back stacks, back button handling,
 * and view state management.
 */
@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
class TicTacToeEspressoTest {

  private val scenarioRule = ActivityScenarioRule(TicTacToeActivity::class.java)
  @get:Rule val rules = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
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

  @Test fun showRenderingTagStaysFresh() {
    // Start a game so that there's something interesting in the Activity window.
    // (Prior screens are all in a dialog window.)

    inAnyView(withId(R.id.login_email)).type("foo@bar")
    inAnyView(withId(R.id.login_password)).type("password")
    inAnyView(withId(R.id.login_button)).perform(click())

    inAnyView(withId(R.id.start_game)).perform(click())

    val environment = AtomicReference<ViewEnvironment>()

    // Why should I learn how to write a matcher when I can just grab the activity
    // and work with it directly?
    scenario.onActivity { activity ->
      val button = activity.findViewById<View>(R.id.game_play_board)
      val parent = button.parent as View
      val rendering = parent.getRendering<GamePlayScreen>()!!
      assertThat(rendering.gameState.playing).isSameInstanceAs(Player.X)
      val firstEnv = parent.environment
      assertThat(firstEnv).isNotNull()
      environment.set(firstEnv)

      // Make a move.
      rendering.onClick(0, 0)
    }

    // I'm not an animal, though. Pop back out to the test to check that the update
    // has happened, to make sure the idle check is allowed to do its thing. (Didn't
    // actually seem to be necessary, originally did everything synchronously in the
    // lambda above and it all worked just fine. But that seems like a land mine.)

    inAnyView(withId(R.id.game_play_toolbar))
      .check(matches(hasDescendant(withText("O, place your ${Player.O.symbol}"))))

    // Now that we're confident the views have updated, back to the activity
    // to mess with what should be the updated rendering.
    scenario.onActivity { activity ->
      val button = activity.findViewById<View>(R.id.game_play_board)
      val parent = button.parent as View
      val rendering = parent.getRendering<GamePlayScreen>()!!
      assertThat(rendering.gameState.playing).isSameInstanceAs(Player.O)
      assertThat(parent.environment).isEqualTo(environment.get())
    }
  }

  @Test fun configChangeReflectsWorkflowState() {
    inAnyView(withId(R.id.login_email)).type("bad email")
    inAnyView(withId(R.id.login_button)).perform(click())

    inAnyView(withId(R.id.login_error_message)).check(matches(withText("Invalid address")))
    rotate()
    inAnyView(withId(R.id.login_error_message)).check(matches(withText("Invalid address")))
  }

  @Test fun editTextSurvivesConfigChange() {
    inAnyView(withId(R.id.login_email)).type("foo@bar")
    inAnyView(withId(R.id.login_password)).type("password")
    rotate()
    inAnyView(withId(R.id.login_email)).check(matches(withText("foo@bar")))
    // Don't save fields that shouldn't be.
    inAnyView(withId(R.id.login_password)).check(matches(withText("")))
  }

  @Test fun backStackPopRestoresViewState() {
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

  @Test fun dialogSurvivesConfigChange() {
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

  @Test fun canGoBackInModalViewAndSeeRestoredViewState() {
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

  @Test fun canGoBackInModalViewAfterConfigChangeAndSeeRestoredViewState() {
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
  @Test fun fullJourney() {
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
