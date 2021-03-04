package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.NamedViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.internal.test.WorkflowUiTestActivity
import com.squareup.workflow1.ui.internal.test.actuallyPressBack
import com.squareup.workflow1.ui.modal.HasModals
import com.squareup.workflow1.ui.modal.ModalViewContainer
import com.squareup.workflow1.ui.plus
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
internal class ComposeViewTreeIntegrationTest {

  @Rule @JvmField val composeRule = createAndroidComposeRule<WorkflowUiTestActivity>()
  private val scenario get() = composeRule.activityRule.scenario

  @Before fun setUp() {
    scenario.onActivity {
      it.viewEnvironment = ViewEnvironment(
        mapOf(
          ViewRegistry to ViewRegistry(
            NoTransitionBackStackContainer,
            NamedViewFactory,
            ModalViewContainer.binding<TestModals>()
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

    assertThat(composedCount).isEqualTo(1)
    assertThat(disposedCount).isEqualTo(0)

    // Navigate away.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }

    assertThat(composedCount).isEqualTo(1)
    assertThat(disposedCount).isEqualTo(1)
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

    assertThat(composedCount).isEqualTo(1)
    assertThat(disposedCount).isEqualTo(0)

    // Navigate away.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }

    assertThat(composedCount).isEqualTo(1)
    assertThat(disposedCount).isEqualTo(1)
  }

  @Test fun composition_state_is_restored_after_config_change() {
    var state: MutableState<String>? = null
    val firstScreen = ComposeRendering("first") {
      val innerState = rememberSaveable { mutableStateOf("hello world") }
      DisposableEffect(Unit) {
        state = innerState
        onDispose { state = null }
      }
    }

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }
    composeRule.runOnIdle {
      assertThat(state!!.value).isEqualTo("hello world")
    }
    state!!.value = "saved"

    // Simulate config change.
    scenario.recreate()

    composeRule.runOnIdle {
      assertThat(state!!.value).isEqualTo("saved")
    }
  }

  @Test fun composition_state_is_restored_on_pop() {
    var state: MutableState<String>? = null
    val firstScreen =
      ComposeRendering("first") {
        val innerState = rememberSaveable { mutableStateOf("hello world") }
        DisposableEffect(Unit) {
          state = innerState
          onDispose { state = null }
        }
      }
    val secondScreen =
      ComposeRendering("second") {}

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }
    composeRule.runOnIdle {
      assertThat(state!!.value).isEqualTo("hello world")
    }
    state!!.value = "saved"

    // Navigate away.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }
    composeRule.runOnIdle {
      assertThat(state).isNull()
    }

    // Navigate back to restore state.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    composeRule.runOnIdle {
      assertThat(state!!.value).isEqualTo("saved")
    }
  }

  @Test fun composition_state_is_restored_on_pop_after_config_change() {
    var state: MutableState<String>? = null
    val firstScreen =
      ComposeRendering("first") {
        val innerState = rememberSaveable { mutableStateOf("hello world") }
        DisposableEffect(Unit) {
          state = innerState
          onDispose { state = null }
        }
      }
    val secondScreen = ComposeRendering("second") {}

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }
    composeRule.runOnIdle {
      assertThat(state!!.value).isEqualTo("hello world")
    }
    state!!.value = "saved"

    // Navigate away.
    scenario.onActivity {
      it.setBackstack(firstScreen, secondScreen)
    }
    composeRule.runOnIdle {
      assertThat(state).isNull()
    }

    // Simulate config change.
    scenario.recreate()

    // Navigate back to restore state.
    scenario.onActivity {
      it.setBackstack(firstScreen)
    }

    composeRule.runOnIdle {
      assertThat(state!!.value).isEqualTo("saved")
    }
  }

  @Test fun composition_state_is_restored_in_modal_after_config_change() {
    var state: MutableState<String>? = null
    val firstScreen = ComposeRendering("first") {
      val innerState = rememberSaveable { mutableStateOf("hello world") }
      DisposableEffect(Unit) {
        state = innerState
        onDispose { state = null }
      }
    }

    // Show first screen to initialize state.
    scenario.onActivity {
      it.setModals(firstScreen)
    }
    composeRule.runOnIdle {
      assertThat(state!!.value).isEqualTo("hello world")
    }
    state!!.value = "saved"

    // Simulate config change.
    scenario.recreate()

    composeRule.runOnIdle {
      assertThat(state!!.value).isEqualTo("saved")
    }
  }

  /**
   * This test is currently broken because of what seems to be an actual compose bug (or two)
   * regarding saved state in dialogs. See:
   *
   * - https://issuetracker.google.com/issues/180124293 (should be fixed in Compose beta02)
   * - https://issuetracker.google.com/issues/180124294
   */
  @Ignore("Compose bugs")
  @Test fun complex_modal_backstack_with_text_entry() {
    var totalCompositions = 0
    fun buildScreen(name: String): ComposeRendering = ComposeRendering(compatibilityKey = name) {
      DisposableEffect(Unit) {
        totalCompositions++
        onDispose {
          totalCompositions--
        }
      }

      var textValue by rememberSaveable { mutableStateOf("") }

      Column(
        Modifier.fillMaxSize().background(Color.White),
        horizontalAlignment = CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        BasicText(name, style = TextStyle(fontWeight = FontWeight.Bold))
        BasicTextField(
          value = textValue,
          onValueChange = { textValue = it },
          modifier = Modifier.testTag("textfield:$name")
        )
      }
    }

    data class Modals(
      override val beneathModals: BackStackScreen<ComposeRendering>,
      override val modals: List<BackStackScreen<ComposeRendering>> = emptyList()
    ) : HasModals<BackStackScreen<ComposeRendering>, BackStackScreen<ComposeRendering>>

    data class AppState(
      val textToEnter: String,
      val screens: Modals,
    ) {
      val topScreen: ComposeRendering
        get() {
          return screens.modals.lastOrNull()?.frames?.lastOrNull()
            ?: screens.beneathModals.frames.last()
        }
    }

    val baseScreen1 = buildScreen("Base One")
    val baseScreen2 = buildScreen("Base Two")
    val firstModal1 = buildScreen("First Modal One")
    val firstModal2 = buildScreen("First Modal Two")
    val secondModal1 = buildScreen("Second Modal One")
    val secondModal2 = buildScreen("Second Modal Two")

    val appStates = listOf(
      AppState(
        textToEnter = "monday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1)
        )
      ),
      AppState(
        textToEnter = "tuesday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1, baseScreen2)
        )
      ),
      AppState(
        textToEnter = "wednesday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1, baseScreen2),
          modals = listOf(
            BackStackScreen(firstModal1)
          )
        )
      ),
      AppState(
        textToEnter = "thursday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1, baseScreen2),
          modals = listOf(
            BackStackScreen(firstModal1, firstModal2)
          )
        )
      ),
      AppState(
        textToEnter = "friday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1, baseScreen2),
          modals = listOf(
            BackStackScreen(firstModal1, firstModal2),
            BackStackScreen(secondModal1)
          )
        )
      ),
      AppState(
        textToEnter = "saturday",
        screens = Modals(
          beneathModals = BackStackScreen(baseScreen1, baseScreen2),
          modals = listOf(
            BackStackScreen(firstModal1, firstModal2),
            BackStackScreen(secondModal1, secondModal2)
          )
        )
      ),
    )

    // Add our custom Modals screen to the view registry.
    scenario.onActivity {
      val registry = it.viewEnvironment[ViewRegistry] + ModalViewContainer.binding<Modals>()
      it.viewEnvironment = it.viewEnvironment + (ViewRegistry to registry)
    }

    // Iterate in order to set the text in all the screens.
    appStates.forEach { appState ->
      scenario.onActivity {
        it.setRendering(appState.screens)
      }
      // Make sure we see the header.
      val topScreenName = appState.topScreen.compatibilityKey
      composeRule.onNodeWithText(topScreenName).assertIsDisplayed()
      composeRule.onNodeWithTag("textfield:$topScreenName")
        .performTextInput(appState.textToEnter)
      composeRule.waitForIdle()
    }

    scenario.recreate()

    // Now iterate backwards to make sure everything was restored.
    appStates.asReversed().forEach { appState ->
      scenario.onActivity {
        // it.setRendering(appState.screens)
      }
      // Make sure we see the header.
      val topScreenName = appState.topScreen.compatibilityKey
      composeRule.onNodeWithText(topScreenName).assertIsDisplayed()
      composeRule.onNodeWithTag("textfield:$topScreenName")
        .assertTextEquals(appState.textToEnter)
      composeRule.waitForIdle()
      actuallyPressBack()
    }
  }

  private fun WorkflowUiTestActivity.setBackstack(vararg backstack: ComposeRendering) {
    setRendering(BackStackScreen(EmptyRendering, backstack.asList()))
  }

  private fun WorkflowUiTestActivity.setModals(vararg modals: ComposeRendering) {
    setRendering(TestModals(modals.asList()))
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

        // Need to set the hash code for persistence.
        id = initialRendering.compatibilityKey.hashCode()

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

  private data class TestModals(
    override val modals: List<ComposeRendering>
  ) : HasModals<ComposeRendering, ComposeRendering> {
    override val beneathModals: ComposeRendering get() = EmptyRendering
  }

  companion object {
    // Use a ComposeView here because the Compose test infra doesn't like it if there are no
    // Compose views at all. See https://issuetracker.google.com/issues/179455327.
    val EmptyRendering = ComposeRendering(compatibilityKey = "") {}
  }
}
