@file:Suppress("TestFunctionName")
@file:OptIn(ExperimentalTestApi::class)

package com.squareup.workflow1.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
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
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.plus
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(WorkflowUiExperimentalApi::class)
internal class WorkflowRenderingTestIos {

  @Test fun doesNotRecompose_whenFactoryChanged() = runComposeUiTest {
    data class TestRendering(
      val text: String
    ) : Screen

    val registry1 = ViewRegistry(
      ScreenComposableFactory<TestRendering> { rendering, _ ->
        BasicText(rendering.text)
      }
    )
    val registry2 = ViewRegistry(
      ScreenComposableFactory<TestRendering> { rendering, _ ->
        BasicText(rendering.text.reversed())
      }
    )
    val registry = mutableStateOf(registry1)

    setContentWithLifecycle {
      WorkflowRendering(TestRendering("hello"), ViewEnvironment.EMPTY + registry.value)
    }

    onNodeWithText("hello").assertIsDisplayed()
    registry.value = registry2
    onNodeWithText("hello").assertIsDisplayed()
    onNodeWithText("olleh").assertDoesNotExist()
  }

  @Test fun wrapsFactoryWithRoot_whenAlreadyInComposition() = runComposeUiTest {
    data class TestRendering(val text: String) : Screen

    val testFactory = ScreenComposableFactory<TestRendering> { rendering, _ ->
      BasicText(rendering.text)
    }
    val viewEnvironment = (ViewEnvironment.EMPTY + ViewRegistry(testFactory))
      .withCompositionRoot { content ->
        Column {
          BasicText("one")
          content()
        }
      }

    setContentWithLifecycle {
      WorkflowRendering(TestRendering("two"), viewEnvironment)
    }

    onNodeWithText("one").assertIsDisplayed()
    onNodeWithText("two").assertIsDisplayed()
  }

  @Test fun destroysChildLifecycle_fromCompose_whenIncompatibleRendering() = runComposeUiTest {
    val lifecycleEvents = mutableListOf<Event>()

    class LifecycleRecorder : ComposableRendering {
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

    class EmptyRendering : ComposableRendering {
      @Composable override fun Content(viewEnvironment: ViewEnvironment) {}
    }

    var rendering: Screen by mutableStateOf(LifecycleRecorder())
    setContentWithLifecycle {
      WorkflowRendering(rendering, env)
    }

    runOnIdle {
      assertEquals(listOf(ON_CREATE, ON_START, ON_RESUME), lifecycleEvents)
      lifecycleEvents.clear()
    }

    rendering = EmptyRendering()

    runOnIdle {
      assertEquals(listOf(ON_PAUSE, ON_STOP, ON_DESTROY), lifecycleEvents)
    }
  }

  @Test fun followsParentLifecycle() = runComposeUiTest {
    val states = mutableListOf<State>()
    val parentOwner = object : LifecycleOwner {
      val registry = LifecycleRegistry(this)
      override val lifecycle: Lifecycle
        get() = registry
    }

    setContentWithLifecycle(parentOwner) {
      WorkflowRendering(LifecycleRecorder(states), env)
    }

    runOnIdle {
      assertEquals(listOf(INITIALIZED), states)
      states.clear()
      parentOwner.registry.currentState = STARTED
    }

    runOnIdle {
      assertEquals(listOf(CREATED, STARTED), states)
      states.clear()
      parentOwner.registry.currentState = CREATED
    }

    runOnIdle {
      assertEquals(listOf(CREATED), states)
      states.clear()
      parentOwner.registry.currentState = RESUMED
    }

    runOnIdle {
      assertEquals(listOf(STARTED, RESUMED), states)
      states.clear()
      parentOwner.registry.currentState = DESTROYED
    }

    runOnIdle {
      assertEquals(listOf(STARTED, CREATED, DESTROYED), states)
    }
  }

  @Test fun handlesParentInitiallyDestroyed() = runComposeUiTest {
    val states = mutableListOf<State>()
    val parentOwner = object : LifecycleOwner {
      val registry = LifecycleRegistry(this)
      override val lifecycle: Lifecycle
        get() = registry
    }
    runOnIdle {
      // Cannot go directly to DESTROYED
      parentOwner.registry.currentState = CREATED
      parentOwner.registry.currentState = DESTROYED
    }

    setContentWithLifecycle(parentOwner) {
      WorkflowRendering(LifecycleRecorder(states), env)
    }

    runOnIdle {
      assertEquals(listOf(INITIALIZED), states)
    }
  }

  @Test fun appliesModifierToComposableContent() = runComposeUiTest {
    class Rendering : ComposableRendering {
      @Composable override fun Content(viewEnvironment: ViewEnvironment) {
        Box(
          Modifier
            .testTag("box")
            .fillMaxSize()
        )
      }
    }

    setContentWithLifecycle {
      WorkflowRendering(
        Rendering(),
        env,
        Modifier.size(width = 42.dp, height = 43.dp)
      )
    }

    onNodeWithTag("box")
      .assertWidthIsEqualTo(42.dp)
      .assertHeightIsEqualTo(43.dp)
  }

  @Test fun propagatesMinConstraints() = runComposeUiTest {
    class Rendering : ComposableRendering {
      @Composable override fun Content(viewEnvironment: ViewEnvironment) {
        Box(Modifier.testTag("box"))
      }
    }

    setContentWithLifecycle {
      WorkflowRendering(
        Rendering(),
        env,
        Modifier.sizeIn(minWidth = 42.dp, minHeight = 43.dp)
      )
    }

    onNodeWithTag("box")
      .assertWidthIsEqualTo(42.dp)
      .assertHeightIsEqualTo(43.dp)
  }

  @Test fun skipsPreviousContentWhenIncompatible() = runComposeUiTest {
    var disposeCount = 0

    class Rendering(
      override val compatibilityKey: String
    ) : ComposableRendering, Compatible {
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
    setContentWithLifecycle {
      WorkflowRendering(Rendering(key), env)
    }

    onNodeWithTag("tag")
      .assertTextEquals("one: 0")
      .performClick()
      .assertTextEquals("one: 1")

    key = "two"

    onNodeWithTag("tag")
      .assertTextEquals("two: 0")
    runOnIdle {
      assertEquals(1, disposeCount)
    }

    key = "one"

    // State should not be restored.
    onNodeWithTag("tag")
      .assertTextEquals("one: 0")
    runOnIdle {
      assertEquals(2, disposeCount)
    }
  }

  @Test fun doesNotSkipPreviousContentWhenCompatible() = runComposeUiTest {
    var disposeCount = 0

    class Rendering(val text: String) : ComposableRendering {
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
    setContentWithLifecycle {
      WorkflowRendering(Rendering(text), env)
    }

    onNodeWithTag("tag")
      .assertTextEquals("one: 0")
      .performClick()
      .assertTextEquals("one: 1")

    text = "two"

    // Counter state should be preserved.
    onNodeWithTag("tag")
      .assertTextEquals("two: 1")
    runOnIdle {
      assertEquals(0, disposeCount)
    }
  }

  private class LifecycleRecorder(
    // For some reason, if we just capture the states val, it is null in the composable.
    private val states: MutableList<State>
  ) : ComposableRendering {
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
            rendering: ScreenT,
            environment: ViewEnvironment
          ) {
            (rendering as ComposableRendering).Content(environment)
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

  private interface ComposableRendering : Screen {
    @Composable fun Content(viewEnvironment: ViewEnvironment)
  }

}
