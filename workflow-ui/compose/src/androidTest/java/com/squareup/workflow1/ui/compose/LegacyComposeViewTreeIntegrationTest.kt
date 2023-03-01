@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnDetachedFromWindow
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.internal.test.LegacyWorkflowUiTestActivity
import com.squareup.workflow1.ui.modal.HasModals
import com.squareup.workflow1.ui.modal.ModalViewContainer
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
internal class LegacyComposeViewTreeIntegrationTest {

  private val composeRule = createAndroidComposeRule<LegacyWorkflowUiTestActivity>()

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(IdleAfterTestRule)
    .around(composeRule)
    .around(IdlingDispatcherRule)

  private val scenario get() = composeRule.activityRule.scenario

  @Before fun setUp() {
    scenario.onActivity {
      it.viewEnvironment = ViewEnvironment(
        mapOf(
          ViewRegistry to ViewRegistry(
            ModalViewContainer.binding<TestModalScreen>(),
            // This now provides a glue binding to the non-legacy BackStackScreen.
            com.squareup.workflow1.ui.backstack.BackStackContainer,
            NoTransitionBackStackContainer,
          )
        )
      )
    }
  }

  @Test fun compose_view_assertions_work() {
    val firstScreen = TestComposeRendering("first") {
      BasicText("First Screen")
    }
    val secondScreen = TestComposeRendering("second") {}

    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    composeRule.onNodeWithText("First Screen").assertIsDisplayed()

    // Navigate away from the first screen.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }

    composeRule.onNodeWithText("First Screen").assertDoesNotExist()
  }

  @Test fun composition_is_disposed_when_navigated_away_dispose_on_detach_strategy() {
    var composedCount = 0
    var disposedCount = 0
    val firstScreen = TestComposeRendering("first", disposeStrategy = DisposeOnDetachedFromWindow) {
      DisposableEffect(Unit) {
        composedCount++
        onDispose {
          disposedCount++
        }
      }
    }
    val secondScreen = TestComposeRendering("second") {}

    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    composeRule.runOnIdle {
      assertThat(composedCount).isEqualTo(1)
      assertThat(disposedCount).isEqualTo(0)
    }

    // Navigate away.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }

    composeRule.runOnIdle {
      assertThat(composedCount).isEqualTo(1)
      assertThat(disposedCount).isEqualTo(1)
    }
  }

  @Test fun composition_is_disposed_when_navigated_away_dispose_on_destroy_strategy() {
    var composedCount = 0
    var disposedCount = 0
    val firstScreen =
      TestComposeRendering("first", disposeStrategy = DisposeOnViewTreeLifecycleDestroyed) {
        DisposableEffect(Unit) {
          composedCount++
          onDispose {
            disposedCount++
          }
        }
      }
    val secondScreen = TestComposeRendering("second") {}

    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    composeRule.runOnIdle {
      assertThat(composedCount).isEqualTo(1)
      assertThat(disposedCount).isEqualTo(0)
    }

    // Navigate away.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }

    composeRule.runOnIdle {
      assertThat(composedCount).isEqualTo(1)
      assertThat(disposedCount).isEqualTo(1)
    }
  }

  @Test fun composition_state_is_restored_after_config_change() {
    val firstScreen = TestComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }
    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 0")
      .performClick()
      .assertTextEquals("Counter: 1")

    // Simulate config change.
    scenario.recreate()

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 1")
  }

  @Test fun composition_state_is_restored_after_navigating_back() {
    val firstScreen = TestComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = TestComposeRendering("second") {
      BasicText("nothing to see here")
    }

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }
    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 0")
      .performClick()
      .assertTextEquals("Counter: 1")

    // Add a screen to the backstack.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }

    composeRule.onNodeWithTag(CounterTag)
      .assertDoesNotExist()

    // Navigate back.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 1")
  }

  @Test
  fun composition_state_is_restored_after_config_change_then_navigating_back() {
    val firstScreen = TestComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = TestComposeRendering("second") {
      BasicText("nothing to see here")
    }

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }
    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 0")
      .performClick()
      .assertTextEquals("Counter: 1")

    // Add a screen to the backstack.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }

    scenario.recreate()

    composeRule.onNodeWithText("nothing to see here")
      .assertIsDisplayed()

    // Navigate to the first screen again.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 1")
  }

  @Test fun composition_state_is_not_restored_after_screen_is_removed_from_backstack() {
    val firstScreen = TestComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = TestComposeRendering("second") {
      BasicText("nothing to see here")
    }

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }
    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 0")
      .performClick()

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 1")

    // Add a screen to the backstack.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }

    // Remove the initial screen from the backstack – this should drop its state.
    scenario.onActivity {
      it.setBackstack(secondScreen)
    }

    // Navigate to the first screen again.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 0")
  }

  @Test
  fun composition_state_is_not_restored_after_screen_is_removed_and_replaced_from_backstack() {
    val firstScreen = TestComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = TestComposeRendering("second") {
      BasicText("nothing to see here")
    }

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }
    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 0")
      .performClick()

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 1")

    // Add a screen to the backstack.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }

    // Remove the initial screen from the backstack – this should drop its state.
    scenario.onActivity {
      it.setBackstack(secondScreen)
    }

    // Put the initial screen back – it should still not have saved state anymore.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }

    // Navigate to the first screen again.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 0")
  }

  @Test fun composition_is_restored_in_modal_after_config_change() {
    val firstScreen = TestComposeRendering(compatibilityKey = "") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setRendering(
        TestModalScreen(
          listOf(
            BackStackScreen(EmptyRendering, firstScreen)
          )
        )
      )
    }

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 0")
      .performClick()
      .assertTextEquals("Counter: 1")

    scenario.recreate()

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 1")
  }

  @Test fun composition_is_restored_in_multiple_modals_after_config_change() {
    val firstScreen = TestComposeRendering(compatibilityKey = "key") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }

    // Use the same compatibility key – these screens are in different modals, so they won't
    // conflict.
    val secondScreen = TestComposeRendering(compatibilityKey = "key") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter2: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag2)
      )
    }

    // Use the same compatibility key – these screens are in different modals, so they won't
    // conflict.
    val thirdScreen = TestComposeRendering(compatibilityKey = "key") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter3: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag3)
      )
    }

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setRendering(
        TestModalScreen(
          listOf(
            firstScreen,
            secondScreen,
            thirdScreen
          )
        )
      )
    }

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 0")
      .performClick()
      .assertTextEquals("Counter: 1")

    composeRule.onNodeWithTag(CounterTag2)
      .assertTextEquals("Counter2: 0")
      .performClick()
      .assertTextEquals("Counter2: 1")

    composeRule.onNodeWithTag(CounterTag3)
      .assertTextEquals("Counter3: 0")
      .performClick()
      .assertTextEquals("Counter3: 1")

    scenario.recreate()

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 1")

    composeRule.onNodeWithTag(CounterTag2)
      .assertTextEquals("Counter2: 1")

    composeRule.onNodeWithTag(CounterTag3)
      .assertTextEquals("Counter3: 1")
  }

  @Test fun composition_is_restored_in_multiple_modals_backstacks_after_config_change() {
    fun createRendering(
      layer: Int,
      screen: Int
    ) = TestComposeRendering(
      // Use the same compatibility key across layers – these screens are in different modals, so
      // they won't conflict.
      compatibilityKey = screen.toString()
    ) {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter[$layer][$screen]: $counter",
        Modifier
          .clickable { counter++ }
          .testTag("L${layer}S$screen")
      )
    }

    val layer0Screen0 = createRendering(0, 0)
    val layer0Screen1 = createRendering(0, 1)
    val layer1Screen0 = createRendering(1, 0)
    val layer1Screen1 = createRendering(1, 1)

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setRendering(
        TestModalScreen(
          listOf(
            BackStackScreen(EmptyRendering, layer0Screen0),
            BackStackScreen(EmptyRendering, layer1Screen0)
          )
        )
      )
    }

    composeRule.onNodeWithTag("L0S0")
      .assertTextEquals("Counter[0][0]: 0")
      .assertIsDisplayed()
      .performClick()
      .assertTextEquals("Counter[0][0]: 1")

    composeRule.onNodeWithTag("L1S0")
      .assertTextEquals("Counter[1][0]: 0")
      .assertIsDisplayed()
      .performClick()
      .assertTextEquals("Counter[1][0]: 1")

    // Push some screens onto the backstack.
    scenario.onActivity {
      it.setRendering(
        TestModalScreen(
          listOf(
            BackStackScreen(EmptyRendering, layer0Screen0, layer0Screen1),
            BackStackScreen(EmptyRendering, layer1Screen0, layer1Screen1)
          )
        )
      )
    }

    composeRule.onNodeWithTag("L0S0")
      .assertDoesNotExist()
    composeRule.onNodeWithTag("L1S0")
      .assertDoesNotExist()

    composeRule.onNodeWithTag("L0S1")
      .assertTextEquals("Counter[0][1]: 0")
      .assertIsDisplayed()
      .performClick()
      .assertTextEquals("Counter[0][1]: 1")

    composeRule.onNodeWithTag("L1S1")
      .assertTextEquals("Counter[1][1]: 0")
      .assertIsDisplayed()
      .performClick()
      .assertTextEquals("Counter[1][1]: 1")

    // Simulate config change.
    scenario.recreate()

    // Check that the last-shown screens were restored.
    composeRule.onNodeWithTag("L0S1")
      .assertIsDisplayed()
    composeRule.onNodeWithTag("L1S1")
      .assertIsDisplayed()

    // Pop both backstacks and check that screens were restored.
    scenario.onActivity {
      it.setRendering(
        TestModalScreen(
          listOf(
            BackStackScreen(EmptyRendering, layer0Screen0),
            BackStackScreen(EmptyRendering, layer1Screen0)
          )
        )
      )
    }

    composeRule.onNodeWithText("Counter[0][0]: 1")
      .assertIsDisplayed()
    composeRule.onNodeWithText("Counter[1][0]: 1")
      .assertIsDisplayed()
  }

  private fun LegacyWorkflowUiTestActivity.setBackstack(vararg backstack: TestComposeRendering) {
    setRendering(BackStackScreen(EmptyRendering, backstack.asList()))
  }

  data class TestModalScreen(
    override val modals: List<Any> = emptyList()
  ) : HasModals<Any, Any> {
    override val beneathModals = EmptyRendering
  }

  data class TestComposeRendering(
    override val compatibilityKey: String,
    val disposeStrategy: ViewCompositionStrategy? = null,
    val content: @Composable () -> Unit
  ) : Compatible, AndroidViewRendering<TestComposeRendering>, ViewFactory<TestComposeRendering> {
    override val type: KClass<in TestComposeRendering> = TestComposeRendering::class
    override val viewFactory: ViewFactory<TestComposeRendering> get() = this

    override fun buildView(
      initialRendering: TestComposeRendering,
      initialViewEnvironment: ViewEnvironment,
      contextForNewView: Context,
      container: ViewGroup?
    ): View {
      var lastCompositionStrategy = initialRendering.disposeStrategy

      return ComposeView(contextForNewView).apply {
        lastCompositionStrategy?.let(::setViewCompositionStrategy)

        bindShowRendering(initialRendering, initialViewEnvironment) { rendering, _ ->
          if (rendering.disposeStrategy != lastCompositionStrategy) {
            lastCompositionStrategy = rendering.disposeStrategy
            lastCompositionStrategy?.let(::setViewCompositionStrategy)
          }

          setContent(rendering.content)
        }
      }
    }
  }

  object EmptyRendering : AndroidScreen<EmptyRendering> {
    override val viewFactory: ScreenViewFactory<EmptyRendering>
      get() = ScreenViewFactory.fromCode { _, e, c, _ ->
        ScreenViewHolder(e, View(c)) { _, _, -> }
      }
  }

  companion object {
    const val CounterTag = "counter"
    const val CounterTag2 = "counter2"
    const val CounterTag3 = "counter3"
  }
}
