@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcherSpy
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackPressedHandlerTest {
  private val scenarioRule = ActivityScenarioRule(ComponentActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  @get:Rule val rules: RuleChain = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)

  private var viewHandlerCount = 0
  private val viewBackHandler: BackPressHandler = {
    viewHandlerCount++
  }

  @Test fun itWorksWhenHandlerIsAddedBeforeAttach() {
    scenario.onActivity { activity ->
      val view = View(activity)
      view.backPressedHandler = viewBackHandler

      activity.setContentView(view)
      assertThat(viewHandlerCount).isEqualTo(0)

      activity.onBackPressed()
      assertThat(viewHandlerCount).isEqualTo(1)
    }
  }

  @Test fun itWorksWhenHandlerIsAddedAfterAttach() {
    scenario.onActivity { activity ->
      val view = View(activity)
      activity.setContentView(view)

      view.backPressedHandler = viewBackHandler
      assertThat(viewHandlerCount).isEqualTo(0)

      activity.onBackPressed()
      assertThat(viewHandlerCount).isEqualTo(1)
    }
  }

  @Test fun onlyActiveWhileViewIsAttached() {
    var fallbackCallCount = 0
    val defaultBackHandler = object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        fallbackCallCount++
      }
    }

    scenario.onActivity { activity ->
      activity.onBackPressedDispatcher.addCallback(defaultBackHandler)

      val view = View(activity)
      view.backPressedHandler = viewBackHandler

      activity.onBackPressed()
      assertThat(fallbackCallCount).isEqualTo(1)
      assertThat(viewHandlerCount).isEqualTo(0)

      activity.setContentView(view)
      activity.onBackPressed()
      assertThat(fallbackCallCount).isEqualTo(1)
      assertThat(viewHandlerCount).isEqualTo(1)

      (view.parent as ViewGroup).removeView(view)
      activity.onBackPressed()
      assertThat(fallbackCallCount).isEqualTo(2)
      assertThat(viewHandlerCount).isEqualTo(1)

      activity.setContentView(view)
      activity.onBackPressed()
      assertThat(fallbackCallCount).isEqualTo(2)
      assertThat(viewHandlerCount).isEqualTo(2)
    }
  }

  @Test fun callbackIsRemoved() {
    scenario.onActivity { activity ->
      val spy = OnBackPressedDispatcherSpy(activity.onBackPressedDispatcher)
      assertThat(spy.callbacks()).isEmpty()

      val lifecycle = LifecycleRegistry(activity)
      lifecycle.currentState = RESUMED

      val view = View(activity)
      view.backPressedHandler = viewBackHandler
      assertThat(spy.callbacks()).hasSize(1)

      ViewTreeLifecycleOwner.set(view) { lifecycle }
      activity.setContentView(view)

      (view.parent as ViewGroup).removeView(view)
      assertThat(spy.callbacks()).hasSize(1)

      lifecycle.currentState = DESTROYED
      assertThat(spy.callbacks()).isEmpty()
    }
  }
}
