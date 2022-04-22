package com.squareup.workflow1.ui.backstack.test

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.test.fixtures.BackStackContainerLifecycleActivity
import com.squareup.workflow1.ui.backstack.test.fixtures.BackStackContainerLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.backstack.test.fixtures.BackStackContainerLifecycleActivity.TestRendering.RecurseRendering
import com.squareup.workflow1.ui.backstack.test.fixtures.NoTransitionBackStackContainer
import com.squareup.workflow1.ui.backstack.test.fixtures.ViewStateTestView
import com.squareup.workflow1.ui.backstack.test.fixtures.viewForScreen
import com.squareup.workflow1.ui.backstack.test.fixtures.waitForScreen
import com.squareup.workflow1.ui.internal.test.DetectLeaksAfterTestSuccess
import com.squareup.workflow1.ui.internal.test.IdlingDispatcherRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Uses a custom subclass, [NoTransitionBackStackContainer], to ensure transitions
 * are synchronous.
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackContainerPersistenceLifecycleTest {

  private val scenarioRule =
    ActivityScenarioRule(BackStackContainerLifecycleActivity::class.java)
  @get:Rule val rules = RuleChain.outerRule(DetectLeaksAfterTestSuccess())
    .around(scenarioRule)
    .around(IdlingDispatcherRule)
  private val scenario get() = scenarioRule.scenario

  // region Basic instance state save/restore tests

  @Test fun restores_view_on_pop_without_config_change() {
    val firstScreen = LeafRendering("initial")
    lateinit var firstView: View

    scenario.onActivity {
      it.update(firstScreen)
    }

    scenario.onActivity {
      // Set some view state to be saved and restored.
      it.currentTestView.viewState = "hello world"
      firstView = it.currentTestView
    }

    // Navigate to another screen.
    scenario.onActivity {
      it.update(firstScreen, LeafRendering("new screen"))
    }
    assertThat(scenario.viewForScreen("new screen").viewState).isEqualTo("")

    // Navigate back.
    scenario.onActivity {
      it.update(firstScreen)
    }

    scenario.viewForScreen("initial").let {
      // Ensure that the view instance wasn't re-used.
      assertThat(it).isNotSameInstanceAs(firstView)

      // Check that the view state was actually restored.
      assertThat(it.viewState).isEqualTo("hello world")
    }
  }

  /**
   * Note that this test passes on older AVDs. It fails reliably on API 32 w/o the fix for
   * https://github.com/square/workflow-kotlin/issues/570
   */
  @Test fun state_restores_after_recreate_without_crashing() {
    assertThat(scenario.state).isEqualTo(RESUMED)

    fun BackStackContainerLifecycleActivity.nestedTestView(): ViewStateTestView {
      val root = rootRenderedView as ViewGroup
      val backStack = root.getChildAt(0) as ViewGroup
      return backStack.getChildAt(0) as ViewStateTestView
    }

    scenario.onActivity {
      it.consumeLifecycleEvents()
      it.setRendering(RecurseRendering(listOf(LeafRendering("nested"))))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents())
        .containsExactly(
          "nested onViewCreated viewState=",
          "nested onShowRendering viewState=",
          "nested onAttach viewState=",
          "LeafView nested ON_CREATE",
          "LeafView nested ON_START",
          "LeafView nested ON_RESUME"
        ).inOrder()
    }

    scenario.onActivity {
      // Set some view state to be saved and restored.
      it.nestedTestView().viewState = "some state"
    }

    // Recreating the activity sends one ON_CREATE event,
    // but the views will step through both INITIALIZED and CREATED before the observer is removed.
    // We're testing that we don't try to restore SavedState when the view is already CREATED.
    scenario.recreate()

    scenario.onActivity {
      // If we get through this, nothing crashed while recreating.
      assertThat(it.consumeLifecycleEvents())
        .containsExactly(
          "activity onCreate",
          "activity onStart",
          "activity onResume"
        ).inOrder()
    }

    scenario.onActivity {
      // We didn't crash, which means we didn't try to restore while CREATED,
      // but make sure that we still restored while the view was in the INITIALIZED state.
      assertThat(it.nestedTestView().viewState).isEqualTo("some state")
    }
  }

  @Test fun restores_current_view_after_config_change() {
    val firstScreen = LeafRendering("initial")

    scenario.onActivity {
      it.update(firstScreen)
    }

    scenario.onActivity {
      // Set some view state to be saved and restored.
      it.currentTestView.viewState = "hello world"
    }

    // Destroy and recreate the activity.
    scenario.recreate()

    // Check that the view state was actually restored.
    scenario.viewForScreen(("initial")).let {
      assertThat(it.viewState).isEqualTo("hello world")
    }
  }

  @Test fun restores_view_on_pop_after_config_change() {
    val firstScreen = LeafRendering("initial")

    scenario.onActivity {
      it.update(firstScreen)
    }

    scenario.onActivity {
      // Set some view state to be saved and restored.
      it.currentTestView.viewState = "hello world"
    }

    // Navigate to another screen.
    // Navigate to another screen.
    scenario.onActivity {
      it.update(firstScreen, LeafRendering("new screen"))
    }
    assertThat(scenario.viewForScreen("new screen").viewState).isEqualTo("")

    // Destroy and recreate the activity.
    scenario.recreate()

    // Navigate back.
    scenario.onActivity {
      it.update(firstScreen)
    }

    // Check that the view state was actually restored.
    assertThat(scenario.viewForScreen("initial").viewState).isEqualTo("hello world")
  }

  @Test fun state_rendering_and_attach_ordering() {
    val firstRendering = LeafRendering("first")
    val secondRendering = LeafRendering("second")

    // Setup initial screen.
    scenario.onActivity {
      it.update(firstRendering)
    }
    waitForScreen(firstRendering.name)
    scenario.onActivity {
      it.currentTestView.viewState = "hello"

      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "first onViewCreated viewState=",
        "first onShowRendering viewState=",
        "first onAttach viewState="
      ).inOrder()
    }

    // Navigate forward.
    scenario.onActivity {
      it.update(firstRendering, secondRendering)
    }
    waitForScreen(secondRendering.name)
    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "second onViewCreated viewState=",
        "second onShowRendering viewState=",
        "first onSave viewState=hello",
        "first onDetach viewState=hello",
        "second onAttach viewState="
      ).inOrder()
    }

    // Navigate back.
    scenario.onActivity {
      it.update(firstRendering)
    }
    waitForScreen(firstRendering.name)
    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "first onViewCreated viewState=",
        "first onShowRendering viewState=",
        "first onRestore viewState=hello",
        "second onDetach viewState=",
        "first onAttach viewState=hello"
      ).inOrder()
    }
  }

  // endregion
  // region Lifecycle tests

  /**
   * We test stop instead of pause because on older Android versions (e.g. level 21),
   * `moveToState(STARTED)` will also stop the lifecycle, not just pause it. By just using stopped,
   * which is consistent across all the versions we care about, we don't need to special-case our
   * assertions, but we're still testing fundamentally the same thing (moving between non-terminal
   * lifecycle states).
   */
  @Test fun lifecycle_stop_then_resume() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
    }

    scenario.onActivity { activity ->
      assertThat(activity.consumeLifecycleEvents()).containsAtLeast(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "initial onAttach viewState=",
        "LeafView initial ON_CREATE",
        "LeafView initial ON_START",
        "LeafView initial ON_RESUME",
      ).inOrder()
    }

    scenario.moveToState(CREATED)

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "LeafView initial ON_PAUSE",
        "activity onPause",
        "LeafView initial ON_STOP",
        "activity onStop",
      ).inOrder()
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

  @Test fun lifecycle_recreate_rendering() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "initial onAttach viewState=",
        "LeafView initial ON_CREATE",
        "LeafView initial ON_START",
        "LeafView initial ON_RESUME",
      ).inOrder()
    }

    scenario.onActivity {
      it.recreateViewsOnNextRendering()
      it.update(LeafRendering("recreated"))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "initial onDetach viewState=",
        "LeafView initial ON_PAUSE",
        "LeafView initial ON_STOP",
        "LeafView initial ON_DESTROY",
        "recreated onAttach viewState=",
        "LeafView recreated ON_CREATE",
        "LeafView recreated ON_START",
        "LeafView recreated ON_RESUME",
      ).inOrder()
    }
  }

  @Test fun lifecycle_recreate_activity() {
    lateinit var initialActivity: BackStackContainerLifecycleActivity

    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "initial onAttach viewState=",
        "LeafView initial ON_CREATE",
        "LeafView initial ON_START",
        "LeafView initial ON_RESUME",
      ).inOrder()

      // Store a reference to the activity so we can get events from it after destroying.
      initialActivity = it

      // Don't call update automatically after restoring, we want to set our own screen with a
      // different rendering.
      it.restoreRenderingAfterConfigChange = false
    }

    scenario.recreate()
    scenario.onActivity {
      it.update(LeafRendering("recreated"))
    }

    scenario.onActivity {
      assertThat(initialActivity.consumeLifecycleEvents()).containsAtLeast(
        "LeafView initial ON_PAUSE",
        "activity onPause",
        "LeafView initial ON_STOP",
        "activity onStop",
        "LeafView initial ON_DESTROY",
        "activity onDestroy",
        "initial onDetach viewState=",
      ).inOrder()

      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "recreated onAttach viewState=",
        "LeafView recreated ON_CREATE",
        "LeafView recreated ON_START",
        "LeafView recreated ON_RESUME",
      ).inOrder()
    }
  }

  @Test fun lifecycle_replace_screen() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "initial onAttach viewState=",
        "LeafView initial ON_CREATE",
        "LeafView initial ON_START",
        "LeafView initial ON_RESUME",
      ).inOrder()
    }

    scenario.onActivity {
      it.update(LeafRendering("next"))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "initial onDetach viewState=",
        "next onAttach viewState=",
        "LeafView next ON_CREATE",
        "LeafView next ON_START",
        "LeafView next ON_RESUME",
        "LeafView initial ON_PAUSE",
        "LeafView initial ON_STOP",
        "LeafView initial ON_DESTROY",
      ).inOrder()
    }
  }

  // https://github.com/square/workflow-kotlin/issues/559
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
  @Test fun lifecycle_replace_after_pause() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.update(LeafRendering("initial"))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "activity onCreate",
        "activity onStart",
        "activity onResume",
        "initial onAttach viewState=",
        "LeafView initial ON_CREATE",
        "LeafView initial ON_START",
        "LeafView initial ON_RESUME",
      ).inOrder()
    }

    scenario.moveToState(STARTED)

    scenario.onActivity {
      it.update(LeafRendering("next"))
    }

    // On SDK 21, every now and then the `onActivity` block after this
    // one fires before ON_START happens. Advance the state again to
    // prevent the race.
    //
    // https://github.com/square/workflow-kotlin/issues/559
    scenario.moveToState(RESUMED)

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "LeafView initial ON_PAUSE",
        "activity onPause",
        "initial onDetach viewState=",
        "next onAttach viewState=",
        "LeafView next ON_CREATE",
        "LeafView next ON_START",
        "LeafView initial ON_STOP",
        "LeafView initial ON_DESTROY",
      ).inOrder()
    }
  }

  @Test fun lifecycle_nested_lifecycle() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.consumeLifecycleEvents()
      it.update(RecurseRendering(listOf(LeafRendering("wrapped"))))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "wrapped onAttach viewState=",
        "LeafView wrapped ON_CREATE",
        "LeafView wrapped ON_START",
        "LeafView wrapped ON_RESUME",
      ).inOrder()
    }

    scenario.onActivity {
      it.update(LeafRendering("unwrapped"))
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "wrapped onDetach viewState=",
        "unwrapped onAttach viewState=",
        "LeafView unwrapped ON_CREATE",
        "LeafView unwrapped ON_START",
        "LeafView unwrapped ON_RESUME",
        "LeafView wrapped ON_PAUSE",
        "LeafView wrapped ON_STOP",
        "LeafView wrapped ON_DESTROY",
      ).inOrder()
    }
  }

  @Test fun lifecycle_is_destroyed_when_navigating_forward() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.consumeLifecycleEvents()
      it.update(
        LeafRendering("first"),
      )
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "first onAttach viewState=",
        "LeafView first ON_CREATE",
        "LeafView first ON_START",
        "LeafView first ON_RESUME",
      ).inOrder()
    }

    scenario.onActivity {
      it.update(
        LeafRendering("first"),
        LeafRendering("second"),
      )
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "first onDetach viewState=",
        "second onAttach viewState=",
        "LeafView second ON_CREATE",
        "LeafView second ON_START",
        "LeafView second ON_RESUME",
        "LeafView first ON_PAUSE",
        "LeafView first ON_STOP",
        "LeafView first ON_DESTROY",
      ).inOrder()
    }
  }

  @Test fun lifecycle_is_destroyed_when_navigating_backwards() {
    assertThat(scenario.state).isEqualTo(RESUMED)
    scenario.onActivity {
      it.consumeLifecycleEvents()
      it.update(
        LeafRendering("first"),
        LeafRendering("second"),
      )
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "second onAttach viewState=",
        "LeafView second ON_CREATE",
        "LeafView second ON_START",
        "LeafView second ON_RESUME",
      ).inOrder()
    }

    scenario.onActivity {
      it.update(
        LeafRendering("first"),
      )
    }

    scenario.onActivity {
      assertThat(it.consumeLifecycleEvents()).containsAtLeast(
        "second onDetach viewState=",
        "first onAttach viewState=",
        "LeafView first ON_CREATE",
        "LeafView first ON_START",
        "LeafView first ON_RESUME",
        "LeafView second ON_PAUSE",
        "LeafView second ON_STOP",
        "LeafView second ON_DESTROY",
      ).inOrder()
    }
  }

  // endregion
}
