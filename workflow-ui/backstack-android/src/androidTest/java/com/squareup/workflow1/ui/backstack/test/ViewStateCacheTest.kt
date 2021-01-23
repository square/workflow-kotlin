package com.squareup.workflow1.ui.backstack.test

import android.os.Parcelable
import android.util.SparseArray
import android.view.AbsSavedState
import android.view.View
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.ViewStateCache
import com.squareup.workflow1.ui.backstack.ViewStateFrame
import com.squareup.workflow1.ui.backstack.test.fixtures.ViewStateTestView
import com.squareup.workflow1.ui.backstack.test.fixtures.ViewStateTestView.SavedState
import com.squareup.workflow1.ui.backstack.test.fixtures.ViewStateTestView.ViewHooks
import com.squareup.workflow1.ui.bindShowRendering
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for the [ViewStateCache]. Note that this class is in the `androidTest` source set
 * instead of the `test` one because it involves non-trivial interactions with view lifecycle
 * methods, and it is risky to rely on fake implementations of views.
 */
@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
class ViewStateCacheTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val viewEnvironment = ViewEnvironment()

  @Test fun restores_state_on_back() = instrumentation.runOnMainSync {
    val cache = ViewStateCache()
    val firstRendering = Named(wrapped = Unit, name = "first")
    val secondRendering = Named(wrapped = Unit, name = "second")
    val firstView = ViewStateTestView(instrumentation.context).apply {
      // Android requires ID to be set for view hierarchy to be saved or restored.
      id = 1
      bindShowRendering(firstRendering, viewEnvironment) { _, _ -> /* Noop */ }
    }
    val secondView = ViewStateTestView(instrumentation.context).apply {
      bindShowRendering(secondRendering, viewEnvironment) { _, _ -> /* Noop */ }
    }

    // Set some state on the first view that will be saved.
    firstView.viewState = "hello world"

    // "Navigate" to the second screen, saving the first screen.
    cache.update(listOf(firstRendering), oldViewMaybe = firstView, newView = secondView)

    // Nothing should read this value again, but clear it to make sure.
    firstView.viewState = "ignored"

    // "Navigate" back to the first screen, restoring state.
    val firstViewRestored = ViewStateTestView(instrumentation.context).apply {
      id = 1
      bindShowRendering(firstRendering, viewEnvironment) { _, _ -> /* Noop */ }
    }
    cache.update(listOf(firstRendering), oldViewMaybe = secondView, newView = firstViewRestored)

    // Check that the state was restored.
    assertThat(firstViewRestored.viewState).isEqualTo("hello world")
  }

  @Test fun doesnt_restore_state_when_restored_view_id_is_different() =
    instrumentation.runOnMainSync {
      val cache = ViewStateCache()
      val firstRendering = Named(wrapped = Unit, name = "first")
      val secondRendering = Named(wrapped = Unit, name = "second")
      val firstView = ViewStateTestView(instrumentation.context).apply {
        // Android requires ID to be set for view hierarchy to be saved or restored.
        id = 1
        bindShowRendering(firstRendering, viewEnvironment) { _, _ -> /* Noop */ }
      }
      val secondView = ViewStateTestView(instrumentation.context).apply {
        bindShowRendering(secondRendering, viewEnvironment) { _, _ -> /* Noop */ }
      }

      // Set some state on the first view that will be saved.
      firstView.viewState = "hello world"

      // "Navigate" to the second screen, saving the first screen.
      cache.update(listOf(firstRendering), oldViewMaybe = firstView, newView = secondView)

      // Nothing should read this value again, but clear it to make sure.
      firstView.viewState = "ignored"

      // "Navigate" back to the first screen, restoring state.
      val firstViewRestored = ViewStateTestView(instrumentation.context).apply {
        id = 2
        bindShowRendering(firstRendering, viewEnvironment) { _, _ -> /* Noop */ }
      }
      cache.update(listOf(firstRendering), oldViewMaybe = secondView, newView = firstViewRestored)

      // Check that the state was restored.
      assertThat(firstViewRestored.viewState).isEqualTo("")
    }

  @Test fun doesnt_restore_state_when_view_id_not_set() = instrumentation.runOnMainSync {
    val cache = ViewStateCache()
    val firstRendering = Named(wrapped = Unit, name = "first")
    val secondRendering = Named(wrapped = Unit, name = "second")
    val firstView = ViewStateTestView(instrumentation.context).apply {
      bindShowRendering(firstRendering, viewEnvironment) { _, _ -> /* Noop */ }
    }
    val secondView = ViewStateTestView(instrumentation.context).apply {
      bindShowRendering(secondRendering, viewEnvironment) { _, _ -> /* Noop */ }
    }

    // Set some state on the first view that will be saved.
    firstView.viewState = "hello world"

    // "Navigate" to the second screen, saving the first screen.
    cache.update(listOf(firstRendering), oldViewMaybe = firstView, newView = secondView)

    // Nothing should read this value again, but clear it to make sure.
    firstView.viewState = "ignored"

    // "Navigate" back to the first screen, restoring state.
    val firstViewRestored = ViewStateTestView(instrumentation.context).apply {
      bindShowRendering(firstRendering, viewEnvironment) { _, _ -> /* Noop */ }
    }
    cache.update(listOf(firstRendering), oldViewMaybe = secondView, newView = firstViewRestored)

    // Check that the state was NOT restored.
    assertThat(firstViewRestored.viewState).isEqualTo("")
  }

  @Test fun throws_when_view_not_bound() = instrumentation.runOnMainSync {
    val cache = ViewStateCache()
    val rendering = Named(wrapped = Unit, name = "duplicate")
    val view = View(instrumentation.context)

    try {
      cache.update(listOf(rendering, rendering), null, view)
      fail("Expected exception.")
    } catch (e: IllegalStateException) {
      assertThat(e.message).contains("to be showing a Named<*> rendering, found null")
    }
  }

  @Test fun throws_on_duplicate_renderings() = instrumentation.runOnMainSync {
    val cache = ViewStateCache()
    val rendering = Named(wrapped = Unit, name = "duplicate")
    val view = View(instrumentation.context).apply {
      bindShowRendering(rendering, viewEnvironment) { _, _ -> /* Noop */ }
    }

    try {
      cache.update(listOf(rendering, rendering), oldViewMaybe = null, newView = view)
      fail("Expected exception.")
    } catch (e: IllegalArgumentException) {
      assertThat(e.message).contains("Duplicate entries not allowed")
    }
  }

  @Test fun lifecycle_started_before_restoreHierarchyState() = instrumentation.runOnMainSync {
    val rendering = Named(wrapped = Unit, name = "foo")
    // Need to initialize the cache with some saved state so it has something to restore.
    val savedState = SparseArray<Parcelable>().apply {
      put(0, SavedState(AbsSavedState.EMPTY_STATE, "hello"))
    }
    val cache = ViewStateCache(
      mutableMapOf(
        rendering.compatibilityKey to ViewStateFrame(rendering.compatibilityKey, savedState)
      )
    )
    var restoreState: State? = null
    val view = ViewStateTestView(instrumentation.context).apply {
      // Must match index in savedState array.
      id = 0
      viewHooks = object : ViewHooks {
        override fun onRestoreInstanceState(view: ViewStateTestView) {
          check(restoreState == null)
          // Don't assert here because if this method doesn't get called it could be a false
          // positive.
          restoreState = ViewTreeLifecycleOwner.get(view)!!.lifecycle.currentState
        }
      }
      bindShowRendering(rendering, viewEnvironment) { _, _ -> /* Noop */ }
    }

    cache.update(listOf(), oldViewMaybe = null, newView = view)

    assertThat(restoreState).isEqualTo(STARTED)
  }

  @Test fun lifecycle_resumed_after_initial_update() = instrumentation.runOnMainSync {
    val cache = ViewStateCache()
    val rendering = Named(wrapped = Unit, name = "foo")
    val view = View(instrumentation.context).apply {
      bindShowRendering(rendering, viewEnvironment) { _, _ -> /* Noop */ }
    }

    cache.update(listOf(), oldViewMaybe = null, newView = view)

    assertThat(ViewTreeLifecycleOwner.get(view)!!.lifecycle.currentState).isEqualTo(RESUMED)
  }

  @Test fun lifecycle_destroyed_after_view_disposed() = instrumentation.runOnMainSync {
    val cache = ViewStateCache()
    val rendering = Named(wrapped = Unit, name = "foo")
    // This is the view under test.
    val viewToBeDisposed = View(instrumentation.context).apply {
      bindShowRendering(rendering, viewEnvironment) { _, _ -> /* Noop */ }
    }
    // Don't care about this view.
    val view = View(instrumentation.context).apply {
      bindShowRendering(rendering, viewEnvironment) { _, _ -> /* Noop */ }
    }

    cache.update(listOf(), oldViewMaybe = viewToBeDisposed, newView = view)

    assertThat(ViewTreeLifecycleOwner.get(view)!!.lifecycle.currentState).isEqualTo(DESTROYED)
  }
}
