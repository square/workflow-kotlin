package com.squareup.workflow1.ui.modal.test

import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import com.squareup.workflow1.ui.internal.test.wrapInLeakCanary
import com.squareup.workflow1.ui.modal.ModalViewContainer
import com.squareup.workflow1.ui.modal.test.ModalViewContainerLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.modal.test.ModalViewContainerLifecycleActivity.TestRendering.RecurseRendering
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Tests for [ModalViewContainer]'s [LifecycleOwner] integration.
 */
internal class ModalViewContainerLifecycleTest {

  private val scenarioRule =
    ActivityScenarioRule(ModalViewContainerLifecycleActivity::class.java)
  @get:Rule val rules = RuleChain.outerRule(scenarioRule)
    .around(IdlingDispatcherRule)
    .wrapInLeakCanary()
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
        "LeafView initial ON_START",
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
      it.recreateViewsOnNextRendering()
      it.update(LeafRendering("recreated"))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsExactly(
        "LeafView initial ON_PAUSE",
        "LeafView initial ON_STOP",
        "LeafView initial ON_DESTROY",
        "LeafView initial onDetached",
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
      it.restoreRenderingAfterConfigChange = false
    }

    scenario.recreate()
    scenario.onActivity {
      assertThat(it).isNotSameInstanceAs(initialActivity)
      it.update(LeafRendering("recreated"))
    }

    scenario.onActivity {
      assertThat(initialActivity.consumeLifecycleEvents()).containsExactly(
        "LeafView initial ON_PAUSE",
        "activity onPause",
        "LeafView initial ON_STOP",
        "activity onStop",
        "LeafView initial onDetached",
        "LeafView initial ON_DESTROY",
        "activity onDestroy",
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

  @Test fun replace_after_stop() {
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

    scenario.moveToState(CREATED)

    scenario.onActivity {
      it.update(LeafRendering("next"))
    }

    scenario.onActivity {
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
        "LeafView wrapped onDetached",
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
      it.recreateViewsOnNextRendering()
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
        "LeafView 2 initial onDetached",
        "LeafView 1 initial ON_PAUSE",
        "LeafView 1 initial ON_STOP",
        "LeafView 1 initial ON_DESTROY",
        "LeafView 1 initial onDetached",
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
