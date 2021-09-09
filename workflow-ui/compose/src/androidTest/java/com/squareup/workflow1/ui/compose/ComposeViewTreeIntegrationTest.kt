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
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.internal.test.WorkflowUiTestActivity
import com.squareup.workflow1.ui.modal.HasModals
import com.squareup.workflow1.ui.modal.ModalViewContainer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
internal class ComposeViewTreeIntegrationTest {

  @get:Rule val composeRule = createAndroidComposeRule<WorkflowUiTestActivity>()
  private val scenario get() = composeRule.activityRule.scenario

  @Before fun setUp() {
    scenario.onActivity {
      it.viewEnvironment = ViewEnvironment(
        mapOf(
          ViewRegistry to ViewRegistry(
            ModalViewContainer.binding<TestModalScreen>(),
            NoTransitionBackStackContainer,
          )
        )
      )
    }
  }

  @Test fun compose_view_assertions_work() {
    val firstScreen = ComposeRendering("first") {
      BasicText("First Screen")
    }
    val secondScreen = ComposeRendering("second") {}

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
    val firstScreen = ComposeRendering("first", disposeStrategy = DisposeOnDetachedFromWindow) {
      DisposableEffect(Unit) {
        composedCount++
        onDispose {
          disposedCount++
        }
      }
    }
    val secondScreen = ComposeRendering("second") {}

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
      ComposeRendering("first", disposeStrategy = DisposeOnViewTreeLifecycleDestroyed) {
        DisposableEffect(Unit) {
          composedCount++
          onDispose {
            disposedCount++
          }
        }
      }
    val secondScreen = ComposeRendering("second") {}

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
    val firstScreen = ComposeRendering("first") {
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
    val firstScreen = ComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = ComposeRendering("second") {
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
    val firstScreen = ComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = ComposeRendering("second") {
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
    val firstScreen = ComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = ComposeRendering("second") {
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
    val firstScreen = ComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = ComposeRendering("second") {
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
    val firstScreen = ComposeRendering(compatibilityKey = "") {
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
    val firstScreen = ComposeRendering(compatibilityKey = "first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = ComposeRendering(compatibilityKey = "second") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter2: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag2)
      )
    }

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setRendering(
        TestModalScreen(
          listOf(
            // Name each BackStackScreen to give them unique state registry keys.
            // TODO(https://github.com/square/workflow-kotlin/issues/469) Should this naming be
            //  done automatically in ModalContainer?
            Named(BackStackScreen(EmptyRendering, firstScreen), "modal1"),
            Named(BackStackScreen(EmptyRendering, secondScreen), "modal2")
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

    scenario.recreate()

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 1")

    composeRule.onNodeWithTag(CounterTag2)
      .assertTextEquals("Counter2: 1")
  }

  private fun WorkflowUiTestActivity.setBackstack(vararg backstack: ComposeRendering) {
    setRendering(BackStackScreen(EmptyRendering, backstack.asList()))
  }

  data class TestModalScreen(
    override val modals: List<Any> = emptyList()
  ) : HasModals<Any, Any> {
    override val beneathModals = EmptyRendering
  }

  data class ComposeRendering(
    override val compatibilityKey: String,
    val disposeStrategy: ViewCompositionStrategy? = null,
    val content: @Composable () -> Unit
  ) : Compatible, AndroidViewRendering<ComposeRendering>, ViewFactory<ComposeRendering> {
    override val type: KClass<in ComposeRendering> = ComposeRendering::class
    override val viewFactory: ViewFactory<ComposeRendering> get() = this

    override fun buildView(
      initialRendering: ComposeRendering,
      initialViewEnvironment: ViewEnvironment,
      contextForNewView: Context,
      container: ViewGroup?
    ): View {
      var lastCompositionStrategy = initialRendering.disposeStrategy

      return ComposeView(contextForNewView).apply {
        lastCompositionStrategy?.let(::setViewCompositionStrategy)

        bindShowRendering() { rendering, _ ->
          if (rendering.disposeStrategy != lastCompositionStrategy) {
            lastCompositionStrategy = rendering.disposeStrategy
            lastCompositionStrategy?.let(::setViewCompositionStrategy)
          }

          setContent(rendering.content)
        }
      }
    }
  }

  companion object {
    // Use a ComposeView here because the Compose test infra doesn't like it if there are no
    // Compose views at all. See https://issuetracker.google.com/issues/179455327.
    val EmptyRendering = ComposeRendering(compatibilityKey = "") {}

    const val CounterTag = "counter"
    const val CounterTag2 = "counter2"
  }
}
