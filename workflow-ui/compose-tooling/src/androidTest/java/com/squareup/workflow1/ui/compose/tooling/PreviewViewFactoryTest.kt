@file:Suppress("TestFunctionName", "PrivatePropertyName")

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
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.WorkflowRendering
import com.squareup.workflow1.ui.compose.composeScreenViewFactory
import com.squareup.workflow1.ui.internal.test.DetectLeaksAfterTestSuccess
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class PreviewViewFactoryTest {

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
    composeScreenViewFactory<TwoStrings> { rendering, environment ->
      Column {
        BasicText(rendering.first.text)
        WorkflowRendering(rendering.second, environment)
      }
    }

  @Preview @Composable
  private fun ParentWithOneChildPreview() {
    ParentWithOneChild.Preview(TwoStrings("one", "two"))
  }

  private val ParentWithTwoChildren =
    composeScreenViewFactory<ThreeStrings> { rendering, environment ->
      Column {
        WorkflowRendering(rendering.first, environment)
        BasicText(rendering.second.text)
        WorkflowRendering(rendering.third, environment)
      }
    }

  @Preview @Composable
  private fun ParentWithTwoChildrenPreview() {
    ParentWithTwoChildren.Preview(ThreeStrings("one", "two", "three"))
  }

  class Leaf(val text: String) : Screen {
    override fun equals(other: Any?): Boolean = (other as? Leaf)?.text == text
    override fun hashCode(): Int = text.hashCode()
    override fun toString(): String = text
  }

  data class TwoStrings(
    val first: Leaf,
    val second: Leaf
  ) : Screen {
    constructor(
      first: String,
      second: String
    ) : this(Leaf(first), Leaf(second))
  }

  data class ThreeStrings(
    val first: Leaf,
    val second: Leaf,
    val third: Leaf
  ) : Screen {
    constructor(
      first: String,
      second: String,
      third: String
    ) : this(Leaf(first), Leaf(second), Leaf(third))
  }

  data class RecursiveRendering(
    val text: String,
    val child: RecursiveRendering? = null
  ) : Screen

  private val ParentRecursive =
    composeScreenViewFactory<RecursiveRendering> { rendering, environment ->
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
      TwoStrings("one", "two"),
      modifier = Modifier.size(0.dp)
    )
  }

  @Preview @Composable
  private fun ParentWithPlaceholderModifier() {
    ParentWithOneChild.Preview(
      TwoStrings("one", "two"),
      placeholderModifier = Modifier.size(0.dp)
    )
  }

  object TestEnvironmentKey : ViewEnvironmentKey<String>(String::class) {
    override val default: String get() = error("Not specified")
  }

  private val ParentConsumesCustomKey = composeScreenViewFactory<TwoStrings> { _, environment ->
    BasicText(environment[TestEnvironmentKey])
  }

  @Preview @Composable
  private fun ParentConsumesCustomKeyPreview() {
    ParentConsumesCustomKey.Preview(TwoStrings("ignored", "ignored")) {
      it + (TestEnvironmentKey to "foo")
    }
  }
}
