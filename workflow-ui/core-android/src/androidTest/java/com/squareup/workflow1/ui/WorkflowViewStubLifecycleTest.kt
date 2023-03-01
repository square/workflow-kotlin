package com.squareup.workflow1.ui

import android.os.Bundle
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.widget.Button
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromCode
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.RecurseRendering
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.ViewRendering
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import leakcanary.DetectLeaksAfterTestSuccess
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Tests for [WorkflowViewStub]'s [LifecycleOwner] integration.
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal class WorkflowViewStubLifecycleTest {

  private val scenarioRule =
    ActivityScenarioRule(WorkflowViewStubLifecycleActivity::class.java)

  @get:Rule val rules = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)
  private val scenario get() = scenarioRule.scenario

  /**
   * We test stop instead of pause because on older Android versions (e.g. level 21),
   * `moveToState(STARTED)` will also stop the lifecycle, not just pause it. By just using stopped,
   * which is consistent across all the versions we care about, we don't need to special-case our
   * assertions, but we're still testing fundamentally the same thing (moving between non-terminal
   * lifecycle states).
   */
  @Test fun stop_then_resume() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "LeafView initial onAttached",
        "LeafView initial ON_CREATE",
        "LeafView initial ON_START",
        "LeafView initial ON_RESUME",
      )
    }

    scenario.moveToState(CREATED)

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView initial ON_PAUSE",
        "activity onPause",
        "LeafView initial ON_STOP",
        "activity onStop",
      )
    }

    scenario.moveToState(RESUMED)

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "activity onStart",
        "activity onResume",
        "LeafView initial ON_START",
        "LeafView initial ON_RESUME",
      )
    }
  }

  @Test fun recreate() {
    lateinit var initialActivity: WorkflowViewStubLifecycleActivity

    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "LeafView initial onAttached",
        "LeafView initial ON_CREATE",
        "LeafView initial ON_START",
        "LeafView initial ON_RESUME",
      )

      // Store a reference to the activity so we can get events from it after destroying.
      initialActivity = it
      it.restoreRenderingAfterConfigChange = false
    }

    scenario.recreate()

    scenario.onActivity {
      it.update(LeafRendering("initial"))
      assertThat(initialActivity.consumeLifecycleEvents()).containsExactly(
        "LeafView initial ON_PAUSE",
        "activity onPause",
        "LeafView initial ON_STOP",
        "activity onStop",
        "LeafView initial ON_DESTROY",
        "activity onDestroy",
        "LeafView initial onDetached",
      )

      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "LeafView initial onAttached",
        "LeafView initial ON_CREATE",
        "LeafView initial ON_START",
        "LeafView initial ON_RESUME",
      )
    }
  }

  @Test fun replace_child() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "LeafView initial onAttached",
        "LeafView initial ON_CREATE",
        "LeafView initial ON_START",
        "LeafView initial ON_RESUME",
      )

      it.update(LeafRendering("next"))

      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView initial onDetached",
        "LeafView initial ON_PAUSE",
        "LeafView initial ON_STOP",
        "LeafView initial ON_DESTROY",
        "LeafView next onAttached",
        "LeafView next ON_CREATE",
        "LeafView next ON_START",
        "LeafView next ON_RESUME",
      )
    }
  }

  @Test fun replace_after_stop() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "LeafView initial onAttached",
        "LeafView initial ON_CREATE",
        "LeafView initial ON_START",
        "LeafView initial ON_RESUME",
      )
    }

    scenario.moveToState(CREATED)

    scenario.onActivity {
      it.update(LeafRendering("next"))

      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView initial ON_PAUSE",
        "activity onPause",
        "LeafView initial ON_STOP",
        "activity onStop",
        "LeafView initial onDetached",
        "LeafView initial ON_DESTROY",
        "LeafView next onAttached",
        "LeafView next ON_CREATE",
      )
    }
  }

  @Test fun nested_lifecycle() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
      it.consumeLifecycleEvents()
      it.update(RecurseRendering(LeafRendering("wrapped")))

      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView initial onDetached",
        "LeafView initial ON_PAUSE",
        "LeafView initial ON_STOP",
        "LeafView initial ON_DESTROY",
        "LeafView wrapped onAttached",
        "LeafView wrapped ON_CREATE",
        "LeafView wrapped ON_START",
        "LeafView wrapped ON_RESUME",
      )

      it.update(LeafRendering("unwrapped"))

      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView wrapped onDetached",
        "LeafView wrapped ON_PAUSE",
        "LeafView wrapped ON_STOP",
        "LeafView wrapped ON_DESTROY",
        "LeafView unwrapped onAttached",
        "LeafView unwrapped ON_CREATE",
        "LeafView unwrapped ON_START",
        "LeafView unwrapped ON_RESUME",
      )
    }
  }

  @Test fun restores_registry_state_on_subview_after_config_change() {
    scenario.onActivity {
      it.update(CounterRendering())
    }

    onView(withTagValue(equalTo(CounterRendering.Tag)))
      .check(matches(withText("Counter: 0")))
      .perform(click())
      .check(matches(withText("Counter: 1")))

    scenario.recreate()

    onView(withTagValue(equalTo(CounterRendering.Tag)))
      .check(matches(withText("Counter: 1")))
  }

  @Test fun propagates_savedstateregistryowner_to_subviews() {
    val expectedRegistryOwner = object : SavedStateRegistryOwner {
      private val controller = SavedStateRegistryController.create(this)
      private val lifecycleRegistry = LifecycleRegistry(this)
      override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

      override fun getLifecycle(): Lifecycle = lifecycleRegistry
    }

    data class RegistrySetter(val wrapped: TestRendering) : ViewRendering<RegistrySetter>() {
      override val viewFactory = fromCode<RegistrySetter> { _, initialEnvironment, context, _ ->
        val stub = WorkflowViewStub(context)
        stub.setViewTreeSavedStateRegistryOwner(expectedRegistryOwner)
        val frame = FrameLayout(context).apply { addView(stub) }

        ScreenViewHolder(initialEnvironment, frame) { rendering, viewEnvironment ->
          stub.show(rendering.wrapped, viewEnvironment)
        }
      }
    }

    var initialRegistryOwner: SavedStateRegistryOwner? = null
    scenario.onActivity {
      it.update(
        RegistrySetter(
          CounterRendering("initial") { view ->
            initialRegistryOwner = view.findViewTreeSavedStateRegistryOwner()
          }
        )
      )
    }

    assertThat(initialRegistryOwner).isSameInstanceAs(expectedRegistryOwner)

    var subsequentRegistryOwner: SavedStateRegistryOwner? = null
    scenario.onActivity {
      it.update(
        RegistrySetter(
          CounterRendering("second") { view ->
            subsequentRegistryOwner = view.findViewTreeSavedStateRegistryOwner()
          }
        )
      )
    }

    assertThat(subsequentRegistryOwner).isSameInstanceAs(expectedRegistryOwner)
  }

  private data class CounterRendering(
    override val compatibilityKey: String = "",
    val onViewAttached: (View) -> Unit = {}
  ) : ViewRendering<CounterRendering>(), Compatible {
    companion object {
      const val Tag = "counter"
    }

    override val viewFactory =
      ScreenViewFactory.fromCode<CounterRendering> { _, initialEnvironment, context, _ ->
        var counter = 0
        val view = Button(context).apply button@{
          tag = Tag

          fun updateText() {
            text = "Counter: $counter"
          }

          addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            lateinit var registryOwner: SavedStateRegistryOwner
            lateinit var lifecycleObserver: LifecycleObserver

            override fun onViewAttachedToWindow(v: View) {
              onViewAttached(this@button)
              registryOwner = this@button.findViewTreeSavedStateRegistryOwner()!!
              lifecycleObserver = object : LifecycleEventObserver {
                override fun onStateChanged(
                  source: LifecycleOwner,
                  event: Event
                ) {
                  if (event == ON_CREATE) {
                    source.lifecycle.removeObserver(this)
                    registryOwner.savedStateRegistry.consumeRestoredStateForKey("counter")
                      ?.let { restoredState ->
                        counter = restoredState.getInt("value")
                        updateText()
                      }
                  }
                }
              }
              registryOwner.lifecycle.addObserver(lifecycleObserver)
              registryOwner.savedStateRegistry.registerSavedStateProvider("counter") {
                Bundle().apply { putInt("value", counter) }
              }
            }

            override fun onViewDetachedFromWindow(v: View) {
              registryOwner.lifecycle.removeObserver(lifecycleObserver)
              registryOwner.savedStateRegistry.unregisterSavedStateProvider("counter")
            }
          })

          updateText()
          setOnClickListener {
            counter++
            updateText()
          }
        }
        ScreenViewHolder(initialEnvironment, view) { _, _ -> /* Noop */ }
      }
  }
}
