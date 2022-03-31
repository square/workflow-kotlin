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
import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.showing
import org.junit.Rule
import org.junit.Test

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackContainerTest {
  @get:Rule val scenarioRule = ActivityScenarioRule(ComponentActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  private data class Rendering(val name: String) : Compatible, AndroidScreen<Rendering> {
    override val compatibilityKey = name
    override val viewFactory: ScreenViewFactory<Rendering>
      get() = ScreenViewFactory.fromCode<Rendering> { _, initialRendering, context, _ ->
        ScreenViewHolder(initialRendering, View(context)) { _, _ -> /* Noop */ }
      }
  }

  @Test fun firstScreenIsRendered() {
    scenario.onActivity { activity ->
      val view = VisibleBackStackContainer(activity)
      val holder = ScreenViewHolder<BackStackScreen<*>>(EMPTY, view) { r, e ->
        view.update(r, e)
      }

      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      val showing = view.visibleRendering as Rendering
      Truth.assertThat(showing).isEqualTo(Rendering("able"))
    }
  }

  @Test fun secondScreenIsRendered() {
    scenario.onActivity { activity ->
      val view = VisibleBackStackContainer(activity)
      val holder = ScreenViewHolder<BackStackScreen<*>>(EMPTY, view) { r, e ->
        view.update(r, e)
      }

      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      holder.show(BackStackScreen(Rendering("baker")), EMPTY)
      val showing = view.visibleRendering as Rendering
      Truth.assertThat(showing).isEqualTo(Rendering("baker"))
    }
  }

  @Test fun thirdScreenIsRendered() {
    scenario.onActivity { activity ->
      val view = VisibleBackStackContainer(activity)
      val holder = ScreenViewHolder<BackStackScreen<*>>(EMPTY, view) { r, e ->
        view.update(r, e)
      }

      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      holder.show(BackStackScreen(Rendering("baker")), EMPTY)
      holder.show(BackStackScreen(Rendering("charlie")), EMPTY)
      val showing = view.visibleRendering as Rendering
      Truth.assertThat(showing).isEqualTo(Rendering("charlie"))

      // This used to fail because of our naive use of TransitionManager. The
      // transition from baker view to charlie view was dropped because the
      // transition from able view to baker view was still in progress.
    }
  }

  @Test fun isDebounced() {
    scenario.onActivity { activity ->
      val view = VisibleBackStackContainer(activity)
      val holder = ScreenViewHolder<BackStackScreen<*>>(EMPTY, view) { r, e ->
        view.update(r, e)
      }

      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      holder.show(BackStackScreen(Rendering("able")), EMPTY)
      holder.show(BackStackScreen(Rendering("able")), EMPTY)

      Truth.assertThat(view.transitionCount).isEqualTo(1)
    }
  }

  private class VisibleBackStackContainer(context: Context) : BackStackContainer(context) {
    var transitionCount = 0
    @Suppress("UNCHECKED_CAST") val visibleRendering: Screen?
      get() = (getChildAt(0)?.tag as NamedScreen<*>).wrapped

    fun show(rendering: BackStackScreen<*>) {
      update(rendering, ViewEnvironment.EMPTY)
    }

    override fun performTransition(
      oldHolderMaybe: ScreenViewHolder<NamedScreen<*>>?,
      newHolder: ScreenViewHolder<NamedScreen<*>>,
      popped: Boolean
    ) {
      transitionCount++
      assertThat(newHolder.view.tag).isNull()
      newHolder.view.tag = newHolder.showing
      super.performTransition(oldHolderMaybe, newHolder, popped)
    }
  }
}
