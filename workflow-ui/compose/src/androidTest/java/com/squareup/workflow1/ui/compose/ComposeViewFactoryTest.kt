package com.squareup.workflow1.ui.compose

import android.content.Context
import android.widget.FrameLayout
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
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
class ComposeViewFactoryTest {

  @get:Rule val composeRule = createComposeRule()

  @Test fun showsComposeContent() {
    val viewFactory = composeViewFactory<Unit> { _, _ ->
      BasicText("Hello, world!")
    }
    val viewRegistry = ViewRegistry(viewFactory)
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))

    composeRule.setContent {
      AndroidView(::RootView) {
        it.stub.update(Unit, viewEnvironment)
      }
    }

    composeRule.onNodeWithText("Hello, world!").assertIsDisplayed()
  }

  @Test fun getsRenderingUpdates() {
    val viewFactory = composeViewFactory<String> { rendering, _ ->
      BasicText(rendering, Modifier.testTag("text"))
    }
    val viewRegistry = ViewRegistry(viewFactory)
    val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to viewRegistry))
    var rendering by mutableStateOf("hello")

    composeRule.setContent {
      AndroidView(::RootView) {
        it.stub.update(rendering, viewEnvironment)
      }
    }
    composeRule.onNodeWithTag("text").assertTextEquals("hello")

    rendering = "world"

    composeRule.onNodeWithTag("text").assertTextEquals("world")
  }

  @Test fun getsViewEnvironmentUpdates() {
    val testEnvironmentKey = object : ViewEnvironmentKey<String>(String::class) {
      override val default: String get() = error("No default")
    }

    val viewFactory = composeViewFactory<Unit> { _, environment ->
      val text = environment[testEnvironmentKey]
      BasicText(text, Modifier.testTag("text"))
    }
    val viewRegistry = ViewRegistry(viewFactory)
    var viewEnvironment by mutableStateOf(
      ViewEnvironment(
        mapOf(
          ViewRegistry to viewRegistry,
          testEnvironmentKey to "hello"
        )
      )
    )

    composeRule.setContent {
      AndroidView(::RootView) {
        it.stub.update(Unit, viewEnvironment)
      }
    }
    composeRule.onNodeWithTag("text").assertTextEquals("hello")

    viewEnvironment = viewEnvironment + (testEnvironmentKey to "world")

    composeRule.onNodeWithTag("text").assertTextEquals("world")
  }

  // TODO(#458) Add test back in once composition root is imported.
  // @Test fun wrapsFactoryWithRoot() {
  //   val wrapperText = mutableStateOf("one")
  //   val viewEnvironment = ViewEnvironment(mapOf(ViewRegistry to ViewRegistry(TestFactory)))
  //     .withCompositionRoot { content ->
  //       Column {
  //         BasicText(wrapperText.value)
  //         content()
  //       }
  //     }
  //
  //   setViewEnvironment(viewEnvironment)
  //
  //   // Compose bug doesn't let us use assertIsDisplayed on older devices.
  //   // See https://issuetracker.google.com/issues/157728188.
  //   composeRule.onNodeWithText("one").assertExists()
  //   composeRule.onNodeWithText("two").assertExists()
  //
  //   wrapperText.value = "ENO"
  //
  //   composeRule.onNodeWithText("ENO").assertExists()
  //   composeRule.onNodeWithText("two").assertExists()
  // }

  private class RootView(context: Context) : FrameLayout(context) {
    val stub = WorkflowViewStub(context).also(::addView)

    fun setViewEnvironment(viewEnvironment: ViewEnvironment) {
      stub.update(TestRendering("two"), viewEnvironment)
    }
  }

  private data class TestRendering(val text: String)

  private companion object {
    val TestFactory = composeViewFactory<TestRendering> { rendering, _ ->
      BasicText(rendering.text)
    }
  }
}
