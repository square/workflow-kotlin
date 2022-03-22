package com.squareup.workflow1.ui.container

import android.content.Context
import android.view.View
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Rule
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackContainerTest {
  @get:Rule val scenarioRule = ActivityScenarioRule(ComponentActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  private data class Rendering(val name: String) : Compatible, AndroidScreen<Rendering> {
    override val compatibilityKey = name
    override val viewFactory: ScreenViewFactory<Rendering>
      get() = ScreenViewFactory<Rendering>(
        buildView = { _, context, _ -> View(context) },
        updateView = { _, _, _ -> /* Noop */ }
      )
  }

  @Test fun firstScreenIsRendered() {
    scenario.onActivity { activity ->
      val c = VisibleBackStackContainer(activity)

      c.show(BackStackScreen(Rendering("able")))
      val showing = c.visibleRendering as Rendering
      Truth.assertThat(showing).isEqualTo(Rendering("able"))
    }
  }

  @Test fun secondScreenIsRendered() {
    scenario.onActivity { activity ->
      val c = VisibleBackStackContainer(activity)

      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("baker")))
      val showing = c.visibleRendering as Rendering
      Truth.assertThat(showing).isEqualTo(Rendering("baker"))
    }
  }

  @Test fun thirdScreenIsRendered() {
    scenario.onActivity { activity ->
      val c = VisibleBackStackContainer(activity)

      c.show(BackStackScreen(Rendering("able")))
      c.show(BackStackScreen(Rendering("baker")))
      c.show(BackStackScreen(Rendering("charlie")))
      val showing = c.visibleRendering as Rendering
      Truth.assertThat(showing).isEqualTo(Rendering("charlie"))

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

      Truth.assertThat(c.transitionCount).isEqualTo(1)
    }
  }

  private class VisibleBackStackContainer(context: Context) : BackStackContainer(context) {
    var transitionCount = 0
    @Suppress("UNCHECKED_CAST") val visibleRendering: Screen?
      get() = (getChildAt(0)?.tag as NamedScreen<*>).wrapped

    fun show(rendering: BackStackScreen<*>) {
      update(rendering, ViewEnvironment.EMPTY + (Screen to rendering))
    }

    override fun performTransition(
      oldHolderMaybe: ScreenViewHolder<NamedScreen<*>>?,
      newHolder: ScreenViewHolder<NamedScreen<*>>,
      popped: Boolean
    ) {
      transitionCount++
      assertThat(newHolder.view.tag).isNull()
      newHolder.view.tag = newHolder.screen
      super.performTransition(oldHolderMaybe, newHolder, popped)
    }
  }
}
