package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentDialog
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
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromCode
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.Wrapper
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.internal.test.WorkflowUiTestActivity
import com.squareup.workflow1.ui.navigation.AndroidOverlay
import com.squareup.workflow1.ui.navigation.BackStackScreen
import com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.navigation.OverlayDialogFactory
import com.squareup.workflow1.ui.navigation.ScreenOverlay
import com.squareup.workflow1.ui.navigation.asDialogHolderWithContent
import com.squareup.workflow1.ui.plus
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.reflect.KClass

internal class ComposeViewTreeIntegrationTest {

  private val composeRule = createAndroidComposeRule<WorkflowUiTestActivity>()

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(IdleAfterTestRule)
    .around(composeRule)
    .around(IdlingDispatcherRule)

  private val scenario get() = composeRule.activityRule.scenario

  @Before fun setUp() {
    scenario.onActivity {
      it.viewEnvironment = (ViewEnvironment.EMPTY + ViewRegistry(NoTransitionBackStackContainer))
        .withComposeInteropSupport()
    }
  }

  @Test fun compose_view_assertions_work() {
    val firstScreen = VanillaComposeRendering("first") {
      BasicText("First Screen")
    }
    val secondScreen = VanillaComposeRendering("second") {}

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

  @Test fun composition_is_disposed_when_navigated_away_stock_class() {
    var composedCount = 0
    var disposedCount = 0
    val firstScreen =
      VanillaComposeRendering("first") {
        DisposableEffect(Unit) {
          composedCount++
          onDispose {
            disposedCount++
          }
        }
      }
    val secondScreen = VanillaComposeRendering("second") {}

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

  @Test fun composition_is_disposed_when_navigated_away_default_strategy() {
    var composedCount = 0
    var disposedCount = 0
    val firstScreen =
      BespokeComposeRendering("first", disposeStrategy = ViewCompositionStrategy.Default) {
        DisposableEffect(Unit) {
          composedCount++
          onDispose {
            disposedCount++
          }
        }
      }
    val secondScreen = VanillaComposeRendering("second") {}

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

  @Test fun composition_is_disposed_when_navigated_away_dispose_on_detach_strategy() {
    var composedCount = 0
    var disposedCount = 0
    val firstScreen =
      BespokeComposeRendering("first", disposeStrategy = DisposeOnDetachedFromWindow) {
        DisposableEffect(Unit) {
          composedCount++
          onDispose {
            disposedCount++
          }
        }
      }
    val secondScreen = VanillaComposeRendering("second") {}

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
      BespokeComposeRendering("first", disposeStrategy = DisposeOnViewTreeLifecycleDestroyed) {
        DisposableEffect(Unit) {
          composedCount++
          onDispose {
            disposedCount++
          }
        }
      }
    val secondScreen = VanillaComposeRendering("second") {}

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
    val firstScreen = VanillaComposeRendering("first") {
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
    val firstScreen = VanillaComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = VanillaComposeRendering("second") {
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
    val firstScreen = VanillaComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = VanillaComposeRendering("second") {
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
    val firstScreen = VanillaComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = VanillaComposeRendering("second") {
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
    val firstScreen = VanillaComposeRendering("first") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }
    val secondScreen = VanillaComposeRendering("second") {
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

  @Test fun composition_is_restored_in_overlay_after_config_change() {
    val firstScreen: Screen = VanillaComposeRendering(compatibilityKey = "") {
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
        BodyAndOverlaysScreen(
          EmptyRendering,
          listOf(TestOverlay(BackStackScreen(EmptyRendering, firstScreen)))
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

  @Test fun composition_is_restored_in_multiple_overlays_after_config_change() {
    val firstScreen: Screen = VanillaComposeRendering(compatibilityKey = "0") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }

    val secondScreen: Screen = VanillaComposeRendering(compatibilityKey = "1") {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter2: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag2)
      )
    }

    val thirdScreen: Screen = VanillaComposeRendering(compatibilityKey = "2") {
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
        BodyAndOverlaysScreen(
          EmptyRendering,
          listOf(
            TestOverlay(firstScreen),
            TestOverlay(secondScreen),
            TestOverlay(thirdScreen)
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

  @Test fun composition_is_restored_in_multiple_overlays_backstacks_after_config_change() {
    fun createRendering(
      layer: Int,
      screen: Int
    ) = VanillaComposeRendering(
      // Use the same compatibility key across layers – these screens are in different overlays, so
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
        BodyAndOverlaysScreen(
          EmptyRendering,
          listOf(
            TestOverlay(BackStackScreen(EmptyRendering, layer0Screen0)),
            // A SavedStateRegistry is set up for each overlay. Each registry needs a unique name,
            // and these names default to their `Compatible.keyFor` value. When we show two
            // of the same type at the same time, we need to give them unique names.
            TestOverlay(NamedScreen(BackStackScreen(EmptyRendering, layer1Screen0), "another"))
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
        BodyAndOverlaysScreen(
          EmptyRendering,
          listOf(
            TestOverlay(BackStackScreen(EmptyRendering, layer0Screen0, layer0Screen1)),
            // A SavedStateRegistry is set up for each overlay. Each registry needs a unique name,
            // and these names default to their `Compatible.keyFor` value. When we show two
            // of the same type at the same time, we need to give them unique names.
            TestOverlay(
              NamedScreen(BackStackScreen(EmptyRendering, layer1Screen0, layer1Screen1), "another")
            )
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
        BodyAndOverlaysScreen(
          EmptyRendering,
          listOf(
            TestOverlay(BackStackScreen(EmptyRendering, layer0Screen0)),
            // A SavedStateRegistry is set up for each overlay. Each registry needs a unique name,
            // and these names default to their `Compatible.keyFor` value. When we show two
            // of the same type at the same time, we need to give them unique names.
            TestOverlay(NamedScreen(BackStackScreen(EmptyRendering, layer1Screen0), "another"))
          )
        )
      )
    }

    composeRule.onNodeWithText("Counter[0][0]: 1")
      .assertIsDisplayed()
    composeRule.onNodeWithText("Counter[1][0]: 1")
      .assertIsDisplayed()
  }

  @Test fun composition_handles_overlay_reordering() {
    val composeA: Screen = VanillaComposeRendering(
      compatibilityKey = "0",
    ) {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }

    val composeB: Screen = VanillaComposeRendering(
      compatibilityKey = "1",
    ) {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter2: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag2)
      )
    }

    scenario.onActivity {
      it.setRendering(
        BodyAndOverlaysScreen(
          EmptyRendering,
          listOf(
            TestOverlay(composeA),
            TestOverlay(composeB),
            // When we move this to the front, both of the other previously-upstream-
            // now-downstream dialogs will be dismissed and re-shown.
            TestOverlay(EmptyRendering)
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

    // Reorder the overlays, dialogs will be dismissed and re-shown to preserve order.

    scenario.onActivity {
      it.setRendering(
        BodyAndOverlaysScreen(
          EmptyRendering,
          listOf(
            TestOverlay(EmptyRendering),
            TestOverlay(composeB),
            TestOverlay(composeA),
          )
        )
      )
    }

    // Are they still responsive?

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 1")
      .performClick()
      .assertTextEquals("Counter: 2")

    composeRule.onNodeWithTag(CounterTag2)
      .assertTextEquals("Counter2: 1")
      .performClick()
      .assertTextEquals("Counter2: 2")
  }

  @Test fun composition_under_view_stub_handles_overlay_reordering() {
    val composeA: Screen = VanillaComposeRendering(
      compatibilityKey = "0",
    ) {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag)
      )
    }

    val composeB: Screen = VanillaComposeRendering(
      compatibilityKey = "1",
    ) {
      var counter by rememberSaveable { mutableStateOf(0) }
      BasicText(
        "Counter2: $counter",
        Modifier
          .clickable { counter++ }
          .testTag(CounterTag2)
      )
    }

    scenario.onActivity {
      it.setRendering(
        BodyAndOverlaysScreen(
          EmptyRendering,
          listOf(
            TestOverlay(ViewStubWrapper(composeA)),
            TestOverlay(ViewStubWrapper(composeB)),
            // When we move this to the front, both of the other previously-upstream-
            // now-downstream dialogs will be dismissed and re-shown.
            TestOverlay(EmptyRendering)
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

    // Reorder the overlays, dialogs will be dismissed and re-shown to preserve order.

    scenario.onActivity {
      it.setRendering(
        BodyAndOverlaysScreen(
          EmptyRendering,
          listOf(
            TestOverlay(EmptyRendering),
            TestOverlay(ViewStubWrapper(composeB)),
            TestOverlay(ViewStubWrapper(composeA)),
          )
        )
      )
    }

    // Are they still responsive?

    composeRule.onNodeWithTag(CounterTag)
      .assertTextEquals("Counter: 1")
      .performClick()
      .assertTextEquals("Counter: 2")

    composeRule.onNodeWithTag(CounterTag2)
      .assertTextEquals("Counter2: 1")
      .performClick()
      .assertTextEquals("Counter2: 2")
  }

  private fun WorkflowUiTestActivity.setBackstack(vararg backstack: Screen) {
    setRendering(
      BackStackScreen.fromList(listOf<AndroidScreen<*>>(EmptyRendering) + backstack.asList())
    )
  }

  data class TestOverlay(
    override val content: Screen
  ) : ScreenOverlay<Screen>, AndroidOverlay<TestOverlay> {
    override fun <U : Screen> map(transform: (Screen) -> U) = error("Not implemented")

    override val dialogFactory =
      OverlayDialogFactory<TestOverlay> { initialRendering, initialEnvironment, context: Context ->
        ComponentDialog(context).asDialogHolderWithContent(initialRendering, initialEnvironment)
      }
  }

  data class ViewStubWrapper<C : Screen>(
    override val content: C
  ) : Screen, Wrapper<Screen, C>, AndroidScreen<ViewStubWrapper<C>> {
    override fun <D : Screen> map(transform: (C) -> D) = ViewStubWrapper(transform(content))

    override val viewFactory: ScreenViewFactory<ViewStubWrapper<C>> =
      fromCode { _, initialEnvironment, context, _ ->
        val stub = WorkflowViewStub(context)

        FrameLayout(context)
          .apply {
            this.addView(stub)
          }.let {
            ScreenViewHolder(initialEnvironment, it) { r, e ->
              stub.show(r.content, e)
            }
          }
      }
  }

  /**
   * This is our own custom lovingly handcrafted implementation that creates [ComposeView]
   * itself, bypassing [ScreenComposableFactory] entirely. Allows us to mess with alternative
   * [ViewCompositionStrategy] approaches.
   */
  data class BespokeComposeRendering(
    override val compatibilityKey: String,
    val disposeStrategy: ViewCompositionStrategy? = null,
    val content: @Composable () -> Unit
  ) : Compatible,
    AndroidScreen<BespokeComposeRendering>,
    ScreenViewFactory<BespokeComposeRendering> {
    override val type: KClass<in BespokeComposeRendering> = BespokeComposeRendering::class
    override val viewFactory: ScreenViewFactory<BespokeComposeRendering> get() = this

    override fun buildView(
      initialRendering: BespokeComposeRendering,
      initialEnvironment: ViewEnvironment,
      context: Context,
      container: ViewGroup?
    ): ScreenViewHolder<BespokeComposeRendering> {
      var lastCompositionStrategy = initialRendering.disposeStrategy

      return ComposeView(context).let { view ->
        lastCompositionStrategy?.let(view::setViewCompositionStrategy)

        ScreenViewHolder(initialEnvironment, view) { rendering, _ ->
          if (rendering.disposeStrategy != lastCompositionStrategy) {
            lastCompositionStrategy = rendering.disposeStrategy
            lastCompositionStrategy?.let { view.setViewCompositionStrategy(it) }
          }

          view.setContent(rendering.content)
        }
      }
    }
  }

  /**
   * Bog standard [ComposeScreen], as opposed to [BespokeComposeRendering].
   * Requires [ViewEnvironment.withComposeInteropSupport].
   */
  data class VanillaComposeRendering(
    override val compatibilityKey: String,
    val content: @Composable () -> Unit
  ) : Compatible, ComposeScreen {
    @Composable override fun Content() {
      content()
    }
  }

  object EmptyRendering : AndroidScreen<EmptyRendering> {
    override val viewFactory: ScreenViewFactory<EmptyRendering>
      get() = ScreenViewFactory.fromCode { _, e, c, _ ->
        ScreenViewHolder(e, View(c)) { _, _ -> }
      }
  }

  companion object {
    const val CounterTag = "counter"
    const val CounterTag2 = "counter2"
    const val CounterTag3 = "counter3"
  }
}
