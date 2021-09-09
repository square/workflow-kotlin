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
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.RecurseRendering
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.ViewRendering
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [WorkflowViewStub]'s [LifecycleOwner] integration.
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal class WorkflowViewStubLifecycleTest {

  @Rule @JvmField internal val scenarioRule =
    ActivityScenarioRule(WorkflowViewStubLifecycleActivity::class.java)
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
      override fun getLifecycle(): Lifecycle = lifecycleRegistry
      override fun getSavedStateRegistry(): SavedStateRegistry = controller.savedStateRegistry
    }

    data class RegistrySetter(val wrapped: TestRendering) : ViewRendering<RegistrySetter>() {
      override val viewFactory: ViewFactory<RegistrySetter> = BuilderViewFactory(
        RegistrySetter::class
      ) { initialRendering, initialViewEnvironment, context, _ ->
        val stub = WorkflowViewStub(context)
        ViewTreeSavedStateRegistryOwner.set(stub, expectedRegistryOwner)

        FrameLayout(context).apply {
          addView(stub)

          bindShowRendering() { r, e ->
            stub.update(r.wrapped, e)
          }
        }
      }
    }

    var initialRegistryOwner: SavedStateRegistryOwner? = null
    scenario.onActivity {
      it.update(RegistrySetter(CounterRendering("initial") { view ->
        initialRegistryOwner = ViewTreeSavedStateRegistryOwner.get(view)
      }))
    }

    assertThat(initialRegistryOwner).isSameInstanceAs(expectedRegistryOwner)

    var subsequentRegistryOwner: SavedStateRegistryOwner? = null
    scenario.onActivity {
      it.update(RegistrySetter(CounterRendering("second") { view ->
        subsequentRegistryOwner = ViewTreeSavedStateRegistryOwner.get(view)
      }))
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

    override val viewFactory: ViewFactory<CounterRendering> = BuilderViewFactory(
      CounterRendering::class
    ) { initialRendering, initialViewEnvironment, context, _ ->
      var counter = 0
      Button(context).apply button@{
        tag = Tag

        fun updateText() {
          text = "Counter: $counter"
        }

        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
          lateinit var registryOwner: SavedStateRegistryOwner
          lateinit var lifecycleObserver: LifecycleObserver

          override fun onViewAttachedToWindow(v: View) {
            onViewAttached(this@button)
            registryOwner = ViewTreeSavedStateRegistryOwner.get(this@button)!!
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

        bindShowRendering() { _, _ ->
          // Noop
        }
      }
    }
  }
}
