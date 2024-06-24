package com.squareup.workflow1.ui.compose

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.plus
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class ScreenComposableFactoryTest {

  private val composeRule = createComposeRule()

  @get:Rule val rules: RuleChain =
    RuleChain.outerRule(DetectLeaksAfterTestSuccess())
      .around(IdleAfterTestRule)
      .around(composeRule)
      .around(IdlingDispatcherRule)

  @Test fun showsComposeContent() {
    val composableFactory = ScreenComposableFactory<TestRendering> { _ ->
      BasicText("Hello, world!")
    }
    val viewEnvironment = (ViewEnvironment.EMPTY + ViewRegistry(composableFactory))
      .withComposeInteropSupport()

    composeRule.setContent {
      AndroidView(::RootView) {
        it.stub.show(TestRendering(), viewEnvironment)
      }
    }

    composeRule.onNodeWithText("Hello, world!").assertIsDisplayed()
  }

  @Test fun getsRenderingUpdates() {
    val composableFactory = ScreenComposableFactory<TestRendering> { rendering ->
      BasicText(rendering.text, Modifier.testTag("text"))
    }
    val viewEnvironment = (ViewEnvironment.EMPTY + ViewRegistry(composableFactory))
      .withComposeInteropSupport()
    var rendering by mutableStateOf(TestRendering("hello"))

    composeRule.setContent {
      AndroidView(::RootView) {
        it.stub.show(rendering, viewEnvironment)
      }
    }
    composeRule.onNodeWithTag("text").assertTextEquals("hello")

    rendering = TestRendering("world")

    composeRule.onNodeWithTag("text").assertTextEquals("world")
  }

  @Test fun getsViewEnvironmentUpdates() {
    val testEnvironmentKey = object : ViewEnvironmentKey<String>() {
      override val default: String get() = error("No default")
    }

    val composableFactory = ScreenComposableFactory<TestRendering> { _ ->
      val text = LocalWorkflowEnvironment.current[testEnvironmentKey]
      BasicText(text, Modifier.testTag("text"))
    }
    val viewRegistry = ViewRegistry(composableFactory)
    var viewEnvironment by mutableStateOf(
      (ViewEnvironment.EMPTY + viewRegistry + (testEnvironmentKey to "hello"))
        .withComposeInteropSupport()
    )

    composeRule.setContent {
      AndroidView(::RootView) {
        it.stub.show(TestRendering(), viewEnvironment)
      }
    }
    composeRule.onNodeWithTag("text").assertTextEquals("hello")

    viewEnvironment = viewEnvironment + (testEnvironmentKey to "world")

    composeRule.onNodeWithTag("text").assertTextEquals("world")
  }

  @Test fun wrapsFactoryWithRoot() {
    val wrapperText = mutableStateOf("one")
    val viewEnvironment = (ViewEnvironment.EMPTY + ViewRegistry(TestFactory))
      .withComposeInteropSupport { content ->
        Column {
          BasicText(wrapperText.value)
          content()
        }
      }
      .withComposeInteropSupport()

    composeRule.setContent {
      AndroidView(::RootView) {
        it.stub.show(TestRendering("two"), viewEnvironment)
      }
    }

    // Compose bug doesn't let us use assertIsDisplayed on older devices.
    // See https://issuetracker.google.com/issues/157728188.
    composeRule.onNodeWithText("one").assertExists()
    composeRule.onNodeWithText("two").assertExists()

    wrapperText.value = "ENO"

    composeRule.onNodeWithText("ENO").assertExists()
    composeRule.onNodeWithText("two").assertExists()
  }

  private class RootView(context: Context) : FrameLayout(context) {
    val stub = WorkflowViewStub(context).also(::addView)
  }

  private data class TestRendering(val text: String = "") : Screen

  private companion object {
    val TestFactory = ScreenComposableFactory<TestRendering> { rendering ->
      BasicText(rendering.text)
    }
  }
}
