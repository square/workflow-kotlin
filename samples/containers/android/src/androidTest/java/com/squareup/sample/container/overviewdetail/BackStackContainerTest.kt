package com.squareup.sample.container.overviewdetail

import android.content.Context
import android.view.View
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.getRendering
import org.junit.Rule
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackContainerTest {
  @get:Rule val scenarioRule = ActivityScenarioRule(ComponentActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  private data class Rendering(val name: String) : Compatible, AndroidViewRendering<Rendering> {
    override val compatibilityKey = name
    override val viewFactory: ViewFactory<Rendering>
      get() = BuilderViewFactory(Rendering::class) { r, e, ctx, _ ->
        View(ctx).also { it.bindShowRendering(r, e) { _, _ -> /* Noop */ } }
      }
  }

  @Test fun firstScreenIsRendered() {
    scenario.onActivity { activity ->
      val c = VisibleBackStackContainer(activity)

      c.show(BackStackScreen(Rendering("able")))
      val showing = c.visibleRendering as Rendering
      assertThat(showing).isEqualTo(Rendering("able"))
    }
  }

  @Test fun secondScreenIsRendered() {
    scenario.onActivity { activity ->
      val c = VisibleBackStackContainer(activity)

      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("baker")))
      val showing = c.visibleRendering as Rendering
      assertThat(showing).isEqualTo(Rendering("baker"))
    }
  }

  @Test fun thirdScreenIsRendered() {
    scenario.onActivity { activity ->
      val c = VisibleBackStackContainer(activity)

      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("baker")))
      c.show(BackStackScreen(Rendering("charlie")))
      val showing = c.visibleRendering as Rendering
      assertThat(showing).isEqualTo(Rendering("charlie"))

      // This used to fail because of our naive use of TransitionManager. The
      // transition from baker view to charlie view was dropped because the
      // transition from able view to baker view was still in progress.
    }
  }

  @Test fun isDebounced() {
    scenario.onActivity { activity ->
      val c = VisibleBackStackContainer(activity)

      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("able")))

      assertThat(c.transitionCount).isEqualTo(1)
    }
  }

  private class VisibleBackStackContainer(context: Context) : BackStackContainer(context) {
    var transitionCount = 0
    val visibleRendering: Any? get() = getChildAt(0)?.getRendering<Named<*>>()?.wrapped

    fun show(rendering: BackStackScreen<*>) {
      update(rendering, ViewEnvironment())
    }

    override fun performTransition(
      oldViewMaybe: View?,
      newView: View,
      popped: Boolean
    ) {
      transitionCount++
      super.performTransition(oldViewMaybe, newView, popped)
    }
  }
}
