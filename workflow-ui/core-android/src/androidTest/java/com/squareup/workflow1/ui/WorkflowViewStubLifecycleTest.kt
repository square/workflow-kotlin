package com.squareup.workflow1.ui

import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.RecurseRendering
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [WorkflowViewStub]'s [LifecycleOwner] integration.
 */
class WorkflowViewStubLifecycleTest {

  @Rule @JvmField internal val scenarioRule =
    ActivityScenarioRule(WorkflowViewStubLifecycleActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  @Test fun pause_then_resume() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
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

    scenario.moveToState(STARTED)

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView initial ON_PAUSE",
        "activity onPause",
      )
    }

    scenario.moveToState(RESUMED)

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "activity onResume",
        "LeafView initial ON_RESUME",
      )
    }
  }

  @Test fun recreate() {
    lateinit var initialActivity: WorkflowViewStubLifecycleActivity

    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
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
    }

    scenario.recreate()

    scenario.onActivity {
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

  @Test fun replace_after_pause() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
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

    scenario.moveToState(STARTED)

    scenario.onActivity {
      it.update(LeafRendering("next"))

      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView initial ON_PAUSE",
        "activity onPause",
        "LeafView initial onDetached",
        "LeafView initial ON_STOP",
        "LeafView initial ON_DESTROY",
        "LeafView next onAttached",
        "LeafView next ON_CREATE",
        "LeafView next ON_START",
      )
    }
  }

  @Test fun nested_lifecycle() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
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
}
