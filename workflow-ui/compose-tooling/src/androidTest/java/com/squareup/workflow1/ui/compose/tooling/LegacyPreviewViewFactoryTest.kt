@file:Suppress("TestFunctionName", "PrivatePropertyName", "DEPRECATION")

package com.squareup.workflow1.ui.compose.tooling

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.composeViewFactory
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class LegacyPreviewViewFactoryTest {

  private val composeRule = createComposeRule()

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(IdleAfterTestRule)
    .around(composeRule)
    .around(IdlingDispatcherRule)

  @Test fun singleChild() {
    composeRule.setContent {
      ParentWithOneChildPreview()
    }

    composeRule.onNodeWithText("one").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
  }

  @Test fun twoChildren() {
    composeRule.setContent {
      ParentWithTwoChildrenPreview()
    }

    composeRule.onNodeWithText("one").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
    composeRule.onNodeWithText("three").assertIsDisplayed()
  }

  @Test fun recursive() {
    composeRule.setContent {
      ParentRecursivePreview()
    }

    composeRule.onNodeWithText("one").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsDisplayed()
    composeRule.onNodeWithText("three").assertIsDisplayed()
  }

  @Test fun modifierIsApplied() {
    composeRule.setContent {
      ParentWithModifier()
    }

    // The view factory will be rendered with size (0,0), so it should be reported as not displayed.
    composeRule.onNodeWithText("one").assertIsNotDisplayed()
    composeRule.onNodeWithText("two").assertIsNotDisplayed()
  }

  @Test fun placeholderModifierIsApplied() {
    composeRule.setContent {
      ParentWithPlaceholderModifier()
    }

    // The child will be rendered with size (0,0), so it should be reported as not displayed.
    composeRule.onNodeWithText("one").assertIsDisplayed()
    composeRule.onNodeWithText("two").assertIsNotDisplayed()
  }

  @Test fun customViewEnvironment() {
    composeRule.setContent {
      ParentConsumesCustomKeyPreview()
    }

    composeRule.onNodeWithText("foo").assertIsDisplayed()
  }

  private val ParentWithOneChild =
    composeViewFactory<Pair<String, String>> { rendering, environment ->
      Column {
        BasicText(rendering.first)
        WorkflowRendering(rendering.second, environment)
      }
    }

  @Preview @Composable
  private fun ParentWithOneChildPreview() {
    ParentWithOneChild.Preview(Pair("one", "two"))
  }

  private val ParentWithTwoChildren =
    composeViewFactory<Triple<String, String, String>> { rendering, environment ->
      Column {
        WorkflowRendering(rendering.first, environment)
        BasicText(rendering.second)
        WorkflowRendering(rendering.third, environment)
      }
    }

  @Preview @Composable
  private fun ParentWithTwoChildrenPreview() {
    ParentWithTwoChildren.Preview(Triple("one", "two", "three"))
  }

  data class RecursiveRendering(
    val text: String,
    val child: RecursiveRendering? = null
  )

  private val ParentRecursive = composeViewFactory<RecursiveRendering> { rendering, environment ->
    Column {
      BasicText(rendering.text)
      rendering.child?.let { child ->
        WorkflowRendering(rendering = child, viewEnvironment = environment)
      }
    }
  }

  @Preview @Composable
  private fun ParentRecursivePreview() {
    ParentRecursive.Preview(
      RecursiveRendering(
        text = "one",
        child = RecursiveRendering(
          text = "two",
          child = RecursiveRendering(text = "three")
        )
      )
    )
  }

  @Preview @Composable
  private fun ParentWithModifier() {
    ParentWithOneChild.Preview(
      Pair("one", "two"),
      modifier = Modifier.size(0.dp)
    )
  }

  @Preview @Composable
  private fun ParentWithPlaceholderModifier() {
    ParentWithOneChild.Preview(
      Pair("one", "two"),
      placeholderModifier = Modifier.size(0.dp)
    )
  }

  object TestEnvironmentKey : ViewEnvironmentKey<String>(String::class) {
    override val default: String get() = error("Not specified")
  }

  private val ParentConsumesCustomKey = composeViewFactory<Unit> { _, environment ->
    BasicText(environment[TestEnvironmentKey])
  }

  @Preview @Composable
  private fun ParentConsumesCustomKeyPreview() {
    ParentConsumesCustomKey.Preview(Unit) {
      it + (TestEnvironmentKey to "foo")
    }
  }
}
