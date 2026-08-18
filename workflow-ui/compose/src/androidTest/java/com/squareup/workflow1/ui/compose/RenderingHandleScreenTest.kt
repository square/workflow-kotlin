@file:OptIn(WorkflowExperimentalApi::class)

package com.squareup.workflow1.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.RenderingHandle
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.renderWorkflowIndirectly
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.internal.test.IdleAfterTestRule
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class RenderingHandleScreenTest {

  private val composeRule = createComposeRule()

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(IdleAfterTestRule)
    .around(composeRule)
    .around(IdlingDispatcherRule)

  @Test fun showsCurrentRenderingOfHandle() {
    val handle = FakeRenderingHandle(TextScreen("hello"))

    composeRule.setContent {
      ViewEnvironment.EMPTY.RootScreen(RenderingHandleScreen(handle))
    }

    composeRule.onNodeWithText("hello").assertIsDisplayed()
  }

  @Test fun updatesWhenHandleRenderingChanges() {
    val handle = FakeRenderingHandle(TextScreen("hello"))

    composeRule.setContent {
      ViewEnvironment.EMPTY.RootScreen(RenderingHandleScreen(handle))
    }

    composeRule.onNodeWithText("hello").assertIsDisplayed()

    // Writing to the handle is all the runtime does – no new parent rendering is emitted.
    composeRule.runOnIdle { handle.currentRendering = TextScreen("world") }

    composeRule.onNodeWithText("world").assertIsDisplayed()
    composeRule.onNodeWithText("hello").assertDoesNotExist()
  }

  @Test fun showsIndirectlyRenderedChildOfRealRuntime() {
    val parent = ParentWorkflow(CounterWorkflow())

    composeRule.setContent {
      val rendering by parent.renderAsState(props = Unit, onOutput = {})
      ViewEnvironment.EMPTY.RootScreen(rendering)
    }

    composeRule.onNodeWithText("count: 0").assertIsDisplayed()

    composeRule.onNodeWithText("count: 0").performClick()

    composeRule.onNodeWithText("count: 1").assertIsDisplayed()
    composeRule.onNodeWithText("count: 0").assertDoesNotExist()
  }

  private data class TextScreen(val text: String) : ComposeScreen {
    @Composable override fun Content() {
      BasicText(text)
    }
  }

  /**
   * Stands in for the runtime's own handle implementation, which is internal to `workflow-runtime`.
   * Like the real thing it backs its rendering with snapshot state.
   */
  private class FakeRenderingHandle(initialRendering: Any) : RenderingHandle() {
    override var currentRendering: Any? by mutableStateOf(initialRendering)
  }

  private data class CounterScreen(
    val count: Int,
    val onClick: () -> Unit
  ) : ComposeScreen {
    @Composable override fun Content() {
      BasicText("count: $count", modifier = Modifier.clickable(onClick = onClick))
    }
  }

  private class CounterWorkflow : StatefulWorkflow<Unit, Int, Nothing, Screen>() {
    override fun initialState(
      props: Unit,
      snapshot: Snapshot?
    ): Int = 0

    override fun render(
      renderProps: Unit,
      renderState: Int,
      context: RenderContext<Unit, Int, Nothing>
    ): Screen = CounterScreen(
      count = renderState,
      onClick = context.eventHandler("increment") { state += 1 }
    )

    override fun snapshotState(state: Int): Snapshot? = null
  }

  private class ParentWorkflow(
    private val child: Workflow<Unit, Nothing, Screen>
  ) : StatefulWorkflow<Unit, Unit, Nothing, Screen>() {
    override fun initialState(
      props: Unit,
      snapshot: Snapshot?
    ) = Unit

    override fun render(
      renderProps: Unit,
      renderState: Unit,
      context: RenderContext<Unit, Unit, Nothing>
    ): Screen = RenderingHandleScreen(context.renderWorkflowIndirectly(child))

    override fun snapshotState(state: Unit): Snapshot? = null
  }
}
