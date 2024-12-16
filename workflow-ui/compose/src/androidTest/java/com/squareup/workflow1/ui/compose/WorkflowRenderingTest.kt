@file:Suppress("TestFunctionName")

package com.squareup.workflow1.ui.compose

import android.view.View
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.Lifecycle.Event.ON_PAUSE
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.ui.withEnvironment
import leakcanary.DetectLeaksAfterTestSuccess
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class WorkflowRenderingTest {

  private val composeRule = createComposeRule()

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(IdleAfterTestRule)
    .around(composeRule)
    .around(IdlingDispatcherRule)

  @Test fun doesNotRecompose_whenFactoryChanged() {
    data class TestRendering(
      val text: String
    ) : Screen

    val registry1 = ViewRegistry(
      ScreenComposableFactory<TestRendering> { rendering ->
        BasicText(rendering.text)
      }
    )
    val registry2 = ViewRegistry(
      ScreenComposableFactory<TestRendering> { rendering ->
        BasicText(rendering.text.reversed())
      }
    )
    val registry = mutableStateOf(registry1)

    composeRule.setContent {
      (ViewEnvironment.EMPTY + registry.value).RootScreen(TestRendering("hello"))
    }

    composeRule.onNodeWithText("hello").assertIsDisplayed()
    registry.value = registry2
    composeRule.onNodeWithText("hello").assertIsDisplayed()
    composeRule.onNodeWithText("olleh").assertDoesNotExist()
  }

  @Test fun wrapsFactoryWithRoot_whenAlreadyInComposition() {
    data class TestRendering(val text: String) : Screen

    val testFactory = ScreenComposableFactory<TestRendering> { rendering ->
      BasicText(rendering.text)
    }
    val viewEnvironment = (ViewEnvironment.EMPTY + ViewRegistry(testFactory))
      .withComposeInteropSupport { content ->
        Column {
          BasicText("one")
          content()
        }
      }

    composeRule.setContent {
      viewEnvironment.RootScreen(TestRendering("two"), Modifier)
    }

    composeRule.onNodeWithText("one").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
  }

  @Test fun legacyAndroidViewRendersUpdates() {
    val wrapperText = mutableStateOf("two")

    composeRule.setContent {
      env.RootScreen(LegacyViewRendering(wrapperText.value))
    }

    onView(withText("two")).check(matches(isDisplayed()))
    wrapperText.value = "OWT"
    onView(withText("OWT")).check(matches(isDisplayed()))
  }

  // https://github.com/square/workflow-kotlin/issues/538
  @Test fun includesSupportForNamed() {
    val wrapperText = mutableStateOf("two")

    composeRule.setContent {
      val rendering = NamedScreen(LegacyViewRendering(wrapperText.value), "fnord")
      env.RootScreen(rendering)
    }

    onView(withText("two")).check(matches(isDisplayed()))
    wrapperText.value = "OWT"
    onView(withText("OWT")).check(matches(isDisplayed()))
  }

  @Test fun namedScreenStaysInTheSameComposeView() {
    composeRule.setContent {
      val outer = LocalView.current

      env.RootScreen(
        NamedScreen(
          name = "fnord",
          content = ComposeScreen {
            val inner = LocalView.current
            assertThat(inner).isSameInstanceAs(outer)

            BasicText("hello", Modifier.testTag("tag"))
          }
        )
      )
    }

    composeRule.onNodeWithTag("tag")
      .assertTextEquals("hello")
  }

  @Test fun environmentScreenStaysInTheSameComposeView() {
    val someKey = object : ViewEnvironmentKey<String>() {
      override val default = "default"
    }

    composeRule.setContent {
      val outer = LocalView.current

      env.RootScreen(
        ComposeScreen {
          val inner = LocalView.current
          assertThat(inner).isSameInstanceAs(outer)

          BasicText(LocalWorkflowEnvironment.current[someKey], Modifier.testTag("tag"))
        }.withEnvironment((someKey to "fnord"))
      )
    }

    composeRule.onNodeWithTag("tag")
      .assertTextEquals("fnord")
  }

  @Test fun destroysChildLifecycle_fromCompose_whenIncompatibleRendering() {
    val lifecycleEvents = mutableListOf<Event>()

    class LifecycleRecorder : ComposableRendering {
      @Composable override fun Content() {
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
          lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
              lifecycleEvents += event
            }
          )
          onDispose {
            // Yes, we're leaking the observer. That's intentional: we need to make sure we see any
            // lifecycle events that happen even after the composable is destroyed.
          }
        }
      }
    }

    class EmptyRendering : ComposableRendering {
      @Composable override fun Content() {}
    }

    var rendering: Screen by mutableStateOf(LifecycleRecorder())
    composeRule.setContent {
      env.RootScreen(rendering)
    }

    composeRule.runOnIdle {
      assertThat(lifecycleEvents).containsExactly(ON_CREATE, ON_START, ON_RESUME).inOrder()
      lifecycleEvents.clear()
    }

    rendering = EmptyRendering()

    composeRule.runOnIdle {
      assertThat(lifecycleEvents).containsExactly(ON_PAUSE, ON_STOP, ON_DESTROY).inOrder()
    }
  }

  @Test fun destroysChildLifecycle_fromLegacyView_whenIncompatibleRendering() {
    val lifecycleEvents = mutableListOf<Event>()

    class LifecycleRecorder : AndroidScreen<LifecycleRecorder> {
      override val viewFactory =
        ScreenViewFactory.fromCode<LifecycleRecorder> { _, initialEnvironment, context, _ ->
          val view = object : View(context) {
            override fun onAttachedToWindow() {
              super.onAttachedToWindow()
              val lifecycle = this.findViewTreeLifecycleOwner()!!.lifecycle
              lifecycle.addObserver(
                LifecycleEventObserver { _, event -> lifecycleEvents += event }
              )
              // Yes, we're leaking the observer. That's intentional: we need to make sure we see
              // any lifecycle events that happen even after the composable is destroyed.
            }
          }
          ScreenViewHolder(initialEnvironment, view) { _, _ -> }
        }
    }

    class EmptyRendering : ComposableRendering {
      @Composable override fun Content() {}
    }

    var rendering: Screen by mutableStateOf(LifecycleRecorder())
    composeRule.setContent {
      env.RootScreen(rendering)
    }

    composeRule.runOnIdle {
      assertThat(lifecycleEvents).containsExactly(ON_CREATE, ON_START, ON_RESUME).inOrder()
      lifecycleEvents.clear()
    }

    rendering = EmptyRendering()

    composeRule.runOnIdle {
      assertThat(lifecycleEvents).containsExactly(ON_PAUSE, ON_STOP, ON_DESTROY).inOrder()
    }
  }

  @Test fun followsParentLifecycle() {
    val states = mutableListOf<State>()
    val parentOwner = object : LifecycleOwner {
      val registry = LifecycleRegistry(this)
      override val lifecycle: Lifecycle
        get() = registry
    }

    composeRule.setContent {
      CompositionLocalProvider(
        androidx.lifecycle.compose.LocalLifecycleOwner provides parentOwner
      ) {
        env.RootScreen(LifecycleRecorder(states))
      }
    }

    composeRule.runOnIdle {
      assertThat(states).containsExactly(INITIALIZED).inOrder()
      states.clear()
      parentOwner.registry.currentState = STARTED
    }

    composeRule.runOnIdle {
      assertThat(states).containsExactly(CREATED, STARTED).inOrder()
      states.clear()
      parentOwner.registry.currentState = CREATED
    }

    composeRule.runOnIdle {
      assertThat(states).containsExactly(CREATED).inOrder()
      states.clear()
      parentOwner.registry.currentState = RESUMED
    }

    composeRule.runOnIdle {
      assertThat(states).containsExactly(STARTED, RESUMED).inOrder()
      states.clear()
      parentOwner.registry.currentState = DESTROYED
    }

    composeRule.runOnIdle {
      assertThat(states).containsExactly(STARTED, CREATED, DESTROYED).inOrder()
    }
  }

  @Test fun handlesParentInitiallyDestroyed() {
    val states = mutableListOf<State>()
    val parentOwner = object : LifecycleOwner {
      val registry = LifecycleRegistry(this)
      override val lifecycle: Lifecycle
        get() = registry
    }
    composeRule.runOnIdle {
      // Cannot go directly to DESTROYED
      parentOwner.registry.currentState = CREATED
      parentOwner.registry.currentState = DESTROYED
    }

    composeRule.setContent {
      CompositionLocalProvider(
        androidx.lifecycle.compose.LocalLifecycleOwner provides parentOwner
      ) {
        env.RootScreen(LifecycleRecorder(states))
      }
    }

    composeRule.runOnIdle {
      assertThat(states).containsExactly(INITIALIZED).inOrder()
    }
  }

  @Test fun appliesModifierToComposableContent() {
    class Rendering : ComposableRendering {
      @Composable override fun Content() {
        Box(
          Modifier
            .testTag("box")
            .fillMaxSize()
        )
      }
    }

    composeRule.setContent {
      env.RootScreen(
        Rendering(),
        Modifier.size(width = 42.dp, height = 43.dp)
      )
    }

    composeRule.onNodeWithTag("box")
      .assertWidthIsEqualTo(42.dp)
      .assertHeightIsEqualTo(43.dp)
  }

  @Test fun propagatesMinConstraints() {
    class Rendering : ComposableRendering {
      @Composable override fun Content() {
        Box(Modifier.testTag("box"))
      }
    }

    composeRule.setContent {
      env.RootScreen(
        Rendering(),
        Modifier.sizeIn(minWidth = 42.dp, minHeight = 43.dp)
      )
    }

    composeRule.onNodeWithTag("box")
      .assertWidthIsEqualTo(42.dp)
      .assertHeightIsEqualTo(43.dp)
  }

  @Test fun appliesModifierToViewContent() {
    val viewId = View.generateViewId()

    class LegacyRendering(private val viewId: Int) : AndroidScreen<LegacyRendering> {
      override val viewFactory =
        ScreenViewFactory.fromCode<LegacyRendering> { _, initialEnvironment, context, _ ->
          val view = View(context)
          ScreenViewHolder(initialEnvironment, view) { rendering, _ ->
            view.id = rendering.viewId
          }
        }
    }

    composeRule.setContent {
      with(LocalDensity.current) {
        env.RootScreen(
          LegacyRendering(viewId),
          Modifier.size(42.toDp(), 43.toDp())
        )
      }
    }

    onView(withId(viewId)).check(matches(hasSize(42, 43)))
  }

  @Test fun skipsPreviousContentWhenIncompatible() {
    var disposeCount = 0

    class Rendering(
      override val compatibilityKey: String
    ) : ComposableRendering, Compatible {
      @Composable override fun Content() {
        var counter by rememberSaveable { mutableStateOf(0) }
        Column {
          BasicText(
            "$compatibilityKey: $counter",
            Modifier
              .testTag("tag")
              .clickable { counter++ }
          )
          DisposableEffect(Unit) {
            onDispose {
              disposeCount++
            }
          }
        }
      }
    }

    var key by mutableStateOf("one")
    composeRule.setContent {
      env.RootScreen(Rendering(key))
    }

    composeRule.onNodeWithTag("tag")
      .assertTextEquals("one: 0")
      .performClick()
      .assertTextEquals("one: 1")

    key = "two"

    composeRule.onNodeWithTag("tag")
      .assertTextEquals("two: 0")
    composeRule.runOnIdle {
      assertThat(disposeCount).isEqualTo(1)
    }

    key = "one"

    // State should not be restored.
    composeRule.onNodeWithTag("tag")
      .assertTextEquals("one: 0")
    composeRule.runOnIdle {
      assertThat(disposeCount).isEqualTo(2)
    }
  }

  @Test fun doesNotSkipPreviousContentWhenCompatible() {
    var disposeCount = 0

    class Rendering(val text: String) : ComposableRendering {
      @Composable override fun Content() {
        var counter by rememberSaveable { mutableStateOf(0) }
        Column {
          BasicText(
            "$text: $counter",
            Modifier
              .testTag("tag")
              .clickable { counter++ }
          )
          DisposableEffect(Unit) {
            onDispose {
              disposeCount++
            }
          }
        }
      }
    }

    var text by mutableStateOf("one")
    composeRule.setContent {
      env.RootScreen(Rendering(text))
    }

    composeRule.onNodeWithTag("tag")
      .assertTextEquals("one: 0")
      .performClick()
      .assertTextEquals("one: 1")

    text = "two"

    // Counter state should be preserved.
    composeRule.onNodeWithTag("tag")
      .assertTextEquals("two: 1")
    composeRule.runOnIdle {
      assertThat(disposeCount).isEqualTo(0)
    }
  }

  @Suppress("SameParameterValue")
  private fun hasSize(
    width: Int,
    height: Int
  ) = object : TypeSafeMatcher<View>() {
    override fun describeTo(description: Description) {
      description.appendText("has size ${width}x${height}px")
    }

    override fun matchesSafely(item: View): Boolean {
      return item.width == width && item.height == height
    }
  }

  private class LifecycleRecorder(
    // For some reason, if we just capture the states val, it is null in the composable.
    private val states: MutableList<State>
  ) : ComposableRendering {
    @Composable override fun Content() {
      val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
      DisposableEffect(lifecycle) {
        this@LifecycleRecorder.states += lifecycle.currentState
        lifecycle.addObserver(
          LifecycleEventObserver { _, _ ->
            this@LifecycleRecorder.states += lifecycle.currentState
          }
        )
        onDispose {
          // Yes, we're leaking the observer. That's intentional: we need to make sure we see any
          // lifecycle events that happen even after the composable is destroyed.
        }
      }
    }
  }

  /**
   * It is significant that this returns a new instance on every call, since we can't rely on real
   * implementations in the wild to reuse the same factory instance across rendering instances.
   */
  private object InefficientComposableFinder : ScreenComposableFactoryFinder {
    override fun <ScreenT : Screen> getComposableFactoryForRendering(
      environment: ViewEnvironment,
      rendering: ScreenT
    ): ScreenComposableFactory<ScreenT>? {
      return if (rendering is ComposableRendering) {
        object : ScreenComposableFactory<ScreenT> {
          override val type: KClass<in ScreenT> get() = error("whatever")

          @Composable override fun Content(
            rendering: ScreenT
          ) {
            (rendering as ComposableRendering).Content()
          }
        }
      } else {
        super.getComposableFactoryForRendering(
          environment,
          rendering
        )
      }
    }
  }

  private val env =
    (ViewEnvironment.EMPTY + (ScreenComposableFactoryFinder to InefficientComposableFinder))
      .withComposeInteropSupport()

  private interface ComposableRendering : Screen {
    @Composable fun Content()
  }

  private data class LegacyViewRendering(val text: String) : AndroidScreen<LegacyViewRendering> {
    override val viewFactory =
      ScreenViewFactory.fromCode<LegacyViewRendering> { _, initialEnvironment, context, _ ->
        val view = TextView(context)
        ScreenViewHolder(initialEnvironment, view) { rendering, _ ->
          view.text = rendering.text
        }
      }
  }
}
