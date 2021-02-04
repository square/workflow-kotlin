package com.squareup.workflow1.ui.modal.test

import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.modal.ModalViewContainer
import com.squareup.workflow1.ui.modal.test.ModalViewContainerLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.modal.test.ModalViewContainerLifecycleActivity.TestRendering.RecurseRendering
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [ModalViewContainer]'s [LifecycleOwner] integration.
 */
class ModalViewContainerLifecycleTest {

  @Rule @JvmField internal val scenarioRule =
    ActivityScenarioRule(ModalViewContainerLifecycleActivity::class.java)
  private val scenario get() = scenarioRule.scenario

  @Test fun pause_then_resume() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
    }

    scenario.onActivity { activity ->
      assertThat(activity.consumeLifecycleEvents()).containsExactly(
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

  @Test fun recreate_rendering() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
    }

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

    scenario.onActivity {
      it.recreateRenderingOnNextUpdate()
      it.update(LeafRendering("recreated"))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView initial ON_PAUSE",
        "LeafView initial ON_STOP",
        "LeafView initial ON_DESTROY",
        "LeafView recreated onAttached",
        "LeafView recreated ON_CREATE",
        "LeafView recreated ON_START",
        "LeafView recreated ON_RESUME",
      )
    }
  }

  @Test fun recreate_activity() {
    lateinit var initialActivity: ModalViewContainerLifecycleActivity

    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
    }

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
      it.update(LeafRendering("recreated"))
    }

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
        "LeafView recreated onAttached",
        "LeafView recreated ON_CREATE",
        "LeafView recreated ON_START",
        "LeafView recreated ON_RESUME",
      )
    }
  }

  @Test fun replace_modal() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
    }

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

    scenario.onActivity {
      it.update(LeafRendering("next"))
    }

    scenario.onActivity {
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
      it.update(LeafRendering("initial"))
    }

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
    }

    scenario.onActivity {
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
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView wrapped onAttached",
        "LeafView wrapped ON_CREATE",
        "LeafView wrapped ON_START",
        "LeafView wrapped ON_RESUME",
      )
    }

    scenario.onActivity {
      it.update(LeafRendering("unwrapped"))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
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

  @Test
  fun separate_modal_lifecycles_are_independent() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(
        LeafRendering("1 initial"),
        LeafRendering("2 initial"),
      )
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "LeafView 1 initial onAttached",
        "LeafView 1 initial ON_CREATE",
        "LeafView 1 initial ON_START",
        "LeafView 1 initial ON_RESUME",
        "LeafView 2 initial onAttached",
        "LeafView 2 initial ON_CREATE",
        "LeafView 2 initial ON_START",
        "LeafView 2 initial ON_RESUME",
      )
    }

    // Change the rendering on only one of the modals.
    scenario.onActivity {
      it.update(
        LeafRendering("1 initial"),
        LeafRendering("2 next"),
      )
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView 2 initial onDetached",
        "LeafView 2 initial ON_PAUSE",
        "LeafView 2 initial ON_STOP",
        "LeafView 2 initial ON_DESTROY",
        "LeafView 2 next onAttached",
        "LeafView 2 next ON_CREATE",
        "LeafView 2 next ON_START",
        "LeafView 2 next ON_RESUME",
      )
    }

    // Change the rendering on the other modal.
    scenario.onActivity {
      it.update(
        LeafRendering("1 next"),
        LeafRendering("2 next"),
      )
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView 1 initial onDetached",
        "LeafView 1 initial ON_PAUSE",
        "LeafView 1 initial ON_STOP",
        "LeafView 1 initial ON_DESTROY",
        "LeafView 1 next onAttached",
        "LeafView 1 next ON_CREATE",
        "LeafView 1 next ON_START",
        "LeafView 1 next ON_RESUME",
      )
    }
  }

  @Test fun all_modals_share_parent_lifecycle() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(
        LeafRendering("1 initial"),
        LeafRendering("2 initial"),
      )
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "LeafView 1 initial onAttached",
        "LeafView 1 initial ON_CREATE",
        "LeafView 1 initial ON_START",
        "LeafView 1 initial ON_RESUME",
        "LeafView 2 initial onAttached",
        "LeafView 2 initial ON_CREATE",
        "LeafView 2 initial ON_START",
        "LeafView 2 initial ON_RESUME",
      )
    }

    scenario.onActivity {
      it.recreateRenderingOnNextUpdate()
      it.update(
        LeafRendering("1 recreated"),
        LeafRendering("2 recreated"),
      )
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView 2 initial ON_PAUSE",
        "LeafView 2 initial ON_STOP",
        "LeafView 2 initial ON_DESTROY",
        "LeafView 1 initial ON_PAUSE",
        "LeafView 1 initial ON_STOP",
        "LeafView 1 initial ON_DESTROY",
        "LeafView 1 recreated onAttached",
        "LeafView 1 recreated ON_CREATE",
        "LeafView 1 recreated ON_START",
        "LeafView 1 recreated ON_RESUME",
        "LeafView 2 recreated onAttached",
        "LeafView 2 recreated ON_CREATE",
        "LeafView 2 recreated ON_START",
        "LeafView 2 recreated ON_RESUME",
      )
    }
  }
}
