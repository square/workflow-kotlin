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
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.lifecycle.ViewTreeLifecycleOwner
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
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.plus
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
      composeScreenViewFactory<TestRendering> { rendering, _ ->
        BasicText(rendering.text)
      }
    )
    val registry2 = ViewRegistry(
      composeScreenViewFactory<TestRendering> { rendering, _ ->
        BasicText(rendering.text.reversed())
      }
    )
    val registry = mutableStateOf(registry1)

    composeRule.setContent {
      WorkflowRendering(TestRendering("hello"), ViewEnvironment.EMPTY + registry.value)
    }

    composeRule.onNodeWithText("hello").assertIsDisplayed()
    registry.value = registry2
    composeRule.onNodeWithText("hello").assertIsDisplayed()
    composeRule.onNodeWithText("olleh").assertDoesNotExist()
  }

  /**
   * Ensures we match the behavior of WorkflowViewStub and other containers, which only check for a
   * new factory when a new rendering is incompatible with the current one.
   */
  @Test fun doesNotRecompose_whenAndroidViewRendering_factoryChanged() {
    data class ShiftyRendering(val whichFactory: Boolean) : AndroidScreen<ShiftyRendering> {
      override val viewFactory: ScreenViewFactory<ShiftyRendering> = when (whichFactory) {
        true -> composeScreenViewFactory { _, _ -> BasicText("one") }
        false -> composeScreenViewFactory { _, _ -> BasicText("two") }
      }
    }

    var rendering by mutableStateOf(ShiftyRendering(true))

    composeRule.setContent {
      WorkflowRendering(rendering, ViewEnvironment.EMPTY)
    }

    composeRule.onNodeWithText("one").assertIsDisplayed()
    rendering = ShiftyRendering(false)
    composeRule.onNodeWithText("one").assertIsDisplayed()
  }

  @Test fun wrapsFactoryWithRoot_whenAlreadyInComposition() {
    data class TestRendering(val text: String) : Screen

    val testFactory = composeScreenViewFactory<TestRendering> { rendering, _ ->
      BasicText(rendering.text)
    }
    val viewEnvironment = (ViewEnvironment.EMPTY + ViewRegistry(testFactory))
      .withCompositionRoot { content ->
        Column {
          BasicText("one")
          content()
        }
      }

    composeRule.setContent {
      WorkflowRendering(TestRendering("two"), viewEnvironment)
    }

    composeRule.onNodeWithText("one").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
  }

  @Test fun legacyAndroidViewRendersUpdates() {
    val wrapperText = mutableStateOf("two")

    composeRule.setContent {
      WorkflowRendering(LegacyViewRendering(wrapperText.value), ViewEnvironment.EMPTY)
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
      WorkflowRendering(rendering, ViewEnvironment.EMPTY)
    }

    onView(withText("two")).check(matches(isDisplayed()))
    wrapperText.value = "OWT"
    onView(withText("OWT")).check(matches(isDisplayed()))
  }

  @Test fun destroysChildLifecycle_fromCompose_whenIncompatibleRendering() {
    val lifecycleEvents = mutableListOf<Event>()

    class LifecycleRecorder : ComposableRendering<LifecycleRecorder> {
      @Composable override fun Content(viewEnvironment: ViewEnvironment) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
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

    class EmptyRendering : ComposableRendering<EmptyRendering> {
      @Composable override fun Content(viewEnvironment: ViewEnvironment) {}
    }

    var rendering: Screen by mutableStateOf(LifecycleRecorder())
    composeRule.setContent {
      WorkflowRendering(rendering, ViewEnvironment.EMPTY)
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
              val lifecycle = ViewTreeLifecycleOwner.get(this)!!.lifecycle
              lifecycle.addObserver(
                LifecycleEventObserver { _, event -> lifecycleEvents += event }
              )
              // Yes, we're leaking the observer. That's intentional: we need to make sure we see
              // any lifecycle events that happen even after the composable is destroyed.
            }
          }
          ScreenViewHolder(initialEnvironment, view) { _, _ -> /* Noop */ }
        }
    }

    class EmptyRendering : ComposableRendering<EmptyRendering> {
      @Composable override fun Content(viewEnvironment: ViewEnvironment) {}
    }

    var rendering: Screen by mutableStateOf(LifecycleRecorder())
    composeRule.setContent {
      WorkflowRendering(rendering, ViewEnvironment.EMPTY)
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
      override fun getLifecycle(): Lifecycle = registry
    }

    composeRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides parentOwner) {
        WorkflowRendering(LifecycleRecorder(states), ViewEnvironment.EMPTY)
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
      override fun getLifecycle(): Lifecycle = registry
    }
    composeRule.runOnIdle {
      parentOwner.registry.currentState = DESTROYED
    }

    composeRule.setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides parentOwner) {
        WorkflowRendering(LifecycleRecorder(states), ViewEnvironment.EMPTY)
      }
    }

    composeRule.runOnIdle {
      assertThat(states).containsExactly(INITIALIZED).inOrder()
    }
  }

  @Test fun appliesModifierToComposableContent() {
    class Rendering : ComposableRendering<Rendering> {
      @Composable override fun Content(viewEnvironment: ViewEnvironment) {
        Box(
          Modifier
            .testTag("box")
            .fillMaxSize()
        )
      }
    }

    composeRule.setContent {
      WorkflowRendering(
        Rendering(),
        ViewEnvironment.EMPTY,
        Modifier.size(width = 42.dp, height = 43.dp)
      )
    }

    composeRule.onNodeWithTag("box")
      .assertWidthIsEqualTo(42.dp)
      .assertHeightIsEqualTo(43.dp)
  }

  @Test fun propagatesMinConstraints() {
    class Rendering : ComposableRendering<Rendering> {
      @Composable override fun Content(viewEnvironment: ViewEnvironment) {
        Box(Modifier.testTag("box"))
      }
    }

    composeRule.setContent {
      WorkflowRendering(
        Rendering(),
        ViewEnvironment.EMPTY,
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
          ScreenViewHolder<LegacyRendering>(initialEnvironment, view) { rendering, _ ->
            view.id = rendering.viewId
          }
        }
    }

    composeRule.setContent {
      with(LocalDensity.current) {
        WorkflowRendering(
          LegacyRendering(viewId),
          ViewEnvironment.EMPTY,
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
    ) : ComposableRendering<Rendering>, Compatible {
      @Composable override fun Content(viewEnvironment: ViewEnvironment) {
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
      WorkflowRendering(Rendering(key), ViewEnvironment.EMPTY)
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

    class Rendering(val text: String) : ComposableRendering<Rendering> {
      @Composable override fun Content(viewEnvironment: ViewEnvironment) {
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
      WorkflowRendering(Rendering(text), ViewEnvironment.EMPTY)
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
  ) : ComposableRendering<LifecycleRecorder> {
    @Composable override fun Content(viewEnvironment: ViewEnvironment) {
      val lifecycle = LocalLifecycleOwner.current.lifecycle
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

  @Suppress("UNCHECKED_CAST")
  private interface ComposableRendering<RenderingT : ComposableRendering<RenderingT>> :
    AndroidScreen<RenderingT> {

    /**
     * It is significant that this returns a new instance on every call, since we can't rely on real
     * implementations in the wild to reuse the same factory instance across rendering instances.
     */
    override val viewFactory: ScreenViewFactory<RenderingT>
      get() = object : ComposeScreenViewFactory<ComposableRendering<*>>() {
        override val type: KClass<in ComposableRendering<*>> = ComposableRendering::class

        @Composable override fun Content(
          rendering: ComposableRendering<*>,
          viewEnvironment: ViewEnvironment
        ) {
          rendering.Content(viewEnvironment)
        }
      }

    @Composable fun Content(viewEnvironment: ViewEnvironment)
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
