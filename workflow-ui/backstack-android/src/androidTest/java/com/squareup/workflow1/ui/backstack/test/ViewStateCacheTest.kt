package com.squareup.workflow1.ui.backstack.test

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewStateFrame
import com.squareup.workflow1.ui.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.ViewStateCache
import com.squareup.workflow1.ui.backstack.test.fixtures.BackStackContainerLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.internal.test.ViewStateTestView
import com.squareup.workflow1.ui.internal.test.requireStateRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith
import kotlin.test.fail

/**
 * Unit tests for the [ViewStateCache]. Note that this class is in the `androidTest` source set
 * instead of the `test` one because it involves non-trivial interactions with view lifecycle
 * methods, and it is risky to rely on fake implementations of views.
 */
@OptIn(WorkflowUiExperimentalApi::class)
@RunWith(AndroidJUnit4::class)
internal class ViewStateCacheTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val viewEnvironment = ViewEnvironment()
  private val parentLifecycle = object : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this).apply {
      currentState = RESUMED
    }
    override fun getLifecycle(): Lifecycle = registry
  }
  private val fakeContainerView = View(instrumentation.context).apply {
    id = 1
  }

  @Test fun saves_and_restores_self() {
    val rendering = Named(wrapped = Unit, name = "rendering")
    val cache = ViewStateCache(
      hiddenViewStates = mutableMapOf(
        rendering.name to createFrameForTest(rendering.name, "hello world")
      )
    ).also {
      it.installOnContainer(fakeContainerView)
    }
    val parcel = Parcel.obtain()

    Bundle().also {
      cache.saveToBundle(it)
      parcel.writeBundle(it)
    }

    val restoredCache = ViewStateCache()
    parcel.setDataPosition(0)
    parcel.readBundle(ViewStateCache::class.java.classLoader)!!.also {
      restoredCache.restoreFromBundle(it)
    }

    assertThat(restoredCache.equalsForTest(cache)).isTrue()
  }

  @Test fun installOn_throws_when_called_twice() {
    val cache = ViewStateCache()
    cache.installOnContainer(fakeContainerView)

    val error = assertFailsWith<IllegalStateException> {
      cache.installOnContainer(fakeContainerView)
    }
    assertThat(error).hasMessageThat()
      .isEqualTo("Expected installOnContainer to only be called once.")
  }

  @Test fun saves_and_restores_child_states_on_navigation() = instrumentation.runOnMainSync {
    val cache = ViewStateCache().also {
      it.installOnContainer(fakeContainerView)
    }
    val firstRendering = Named(wrapped = Unit, name = "first")
    val secondRendering = Named(wrapped = Unit, name = "second")
    // Android requires ID to be set for view hierarchy to be saved or restored.
    val firstView = createTestView(firstRendering, id = 1)
    val secondView = createTestView(secondRendering)

    // "Navigate" to the first screen to initialize it.
    cache.update(emptyList(), oldViewMaybe = null, newView = firstView)

    // Set some state on the first view that will be saved.
    firstView.viewState = "hello world"

    // "Navigate" to the second screen, saving the first screen.
    cache.update(
      listOf(firstRendering),
      oldViewMaybe = firstView,
      newView = secondView
    )

    // Nothing should read this value again, but clear it to make sure.
    firstView.viewState = "ignored"

    // "Navigate" back to the first screen, restoring state.
    val firstViewRestored = createTestView(firstRendering, id = 1)
    cache.update(emptyList(), oldViewMaybe = secondView, newView = firstViewRestored)

    // Check that the state was restored.
    assertThat(firstViewRestored.viewState).isEqualTo("hello world")
  }

  @Test fun doesnt_restore_state_when_restored_view_id_is_different() =
    instrumentation.runOnMainSync {
      val cache = ViewStateCache().also {
        it.installOnContainer(fakeContainerView)
      }
      val firstRendering = Named(wrapped = Unit, name = "first")
      val secondRendering = Named(wrapped = Unit, name = "second")
      // Android requires ID to be set for view hierarchy to be saved or restored.
      val firstView = createTestView(firstRendering, id = 1)
      val secondView = createTestView(secondRendering)

      // "Navigate" to the first screen to initialize it.
      cache.update(emptyList(), oldViewMaybe = null, newView = firstView)

      // Set some state on the first view that will be saved.
      firstView.viewState = "hello world"

      // "Navigate" to the second screen, saving the first screen.
      cache.update(
        listOf(firstRendering),
        oldViewMaybe = firstView,
        newView = secondView
      )

      // Nothing should read this value again, but clear it to make sure.
      firstView.viewState = "ignored"

      // "Navigate" back to the first screen, restoring state.
      val firstViewRestored = ViewStateTestView<LeafRendering>(instrumentation.context).apply {
        id = 2
        bindShowRendering(firstRendering, viewEnvironment) { _, _ -> /* Noop */ }
        WorkflowLifecycleOwner.installOn(this)
      }
      cache.update(emptyList(), oldViewMaybe = secondView, newView = firstViewRestored)

      // Check that the state was restored.
      assertThat(firstViewRestored.viewState).isEqualTo("")
    }

  @Test fun doesnt_restore_state_when_view_id_not_set() = instrumentation.runOnMainSync {
    val cache = ViewStateCache().also {
      it.installOnContainer(fakeContainerView)
    }
    val firstRendering = Named(wrapped = Unit, name = "first")
    val secondRendering = Named(wrapped = Unit, name = "second")
    val firstView = createTestView(firstRendering)
    val secondView = createTestView(secondRendering)

    // "Navigate" to the first screen to initialize it.
    cache.update(emptyList(), oldViewMaybe = null, newView = firstView)

    // Set some state on the first view that will be saved.
    firstView.viewState = "hello world"

    // "Navigate" to the second screen, saving the first screen.
    cache.update(
      listOf(firstRendering),
      oldViewMaybe = firstView,
      newView = secondView
    )

    // Nothing should read this value again, but clear it to make sure.
    firstView.viewState = "ignored"

    // "Navigate" back to the first screen, restoring state.
    val firstViewRestored = createTestView(firstRendering)
    cache.update(emptyList(), oldViewMaybe = secondView, newView = firstViewRestored)

    // Check that the state was NOT restored.
    assertThat(firstViewRestored.viewState).isEqualTo("")
  }

  @Test fun throws_when_view_not_bound() = instrumentation.runOnMainSync {
    val cache = ViewStateCache().also {
      it.installOnContainer(fakeContainerView)
    }
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
    val cache = ViewStateCache().also {
      it.installOnContainer(fakeContainerView)
    }
    val rendering = Named(wrapped = Unit, name = "duplicate")
    val view = createTestView(rendering)

    try {
      cache.update(listOf(rendering, rendering), null, view)
      fail("Expected exception.")
    } catch (e: IllegalArgumentException) {
      assertThat(e.message).contains("Duplicate entries not allowed")
    }
  }

  @Test fun saveInstanceState_works() = instrumentation.runOnMainSync {
    val cache = ViewStateCache().also {
      it.installOnContainer(fakeContainerView)
    }
    val firstRendering = Named(wrapped = Unit, name = "first")
    val secondRendering = Named(wrapped = Unit, name = "second")
    val firstView = createTestView(firstRendering)
    val secondView = createTestView(secondRendering)
    var firstState = ""

    // "Navigate" to the first screen to initialize it.
    cache.update(emptyList(), oldViewMaybe = null, newView = firstView)
    val firstStateRegistry = firstView.requireStateRegistry()

    // Set some state on the first view that will be saved.
    firstStateRegistry.registerSavedStateProvider("state") {
      Bundle().apply {
        putString("state", firstState)
      }
    }
    firstState = "hello world"

    // "Navigate" to the second screen, saving the first screen.
    cache.update(
      listOf(firstRendering),
      oldViewMaybe = firstView,
      newView = secondView
    )

    // The state provider not read this value again, but change it to make sure.
    firstState = "ignored"

    // "Navigate" back to the first screen, restoring state.
    val firstViewRestored = createTestView(firstRendering, id = 1)
    cache.update(emptyList(), oldViewMaybe = secondView, newView = firstViewRestored)
    val restoredStateRegistry = firstViewRestored.requireStateRegistry()

    // Check that the state was restored.
    restoredStateRegistry.consumeRestoredStateForKey("state")!!.let { restoredBUndle ->
      assertThat(restoredBUndle.size()).isEqualTo(1)
      assertThat(restoredBUndle.getString("state")).isEqualTo("hello world")
    }
  }

  private fun createTestView(
    firstRendering: Named<Unit>,
    id: Int? = null
  ) = ViewStateTestView<LeafRendering>(instrumentation.context).also { view ->
    id?.let { view.id = id }
    view.bindShowRendering(firstRendering, viewEnvironment) { _, _ -> /* Noop */ }
    WorkflowLifecycleOwner.installOn(view, parentLifecycle::registry)
  }

  private fun createFrameForTest(
    key: String,
    fakeState: String
  ): ViewStateFrame = ViewStateFrame(
    key,
    viewState = null,
    androidXBundle = Bundle().apply { putString(FAKE_STATE_KEY, fakeState) }
  )

  private fun ViewStateCache.equalsForTest(other: ViewStateCache): Boolean {
    if (hiddenViewStates.size != other.hiddenViewStates.size) return false
    hiddenViewStates.entries.sortedBy { it.key }
      .zip(other.hiddenViewStates.entries.sortedBy { it.key })
      .forEach { (leftEntry, rightEntry) ->
        if (leftEntry.key != rightEntry.key) return false
        if (!leftEntry.value.equalsForTest(rightEntry.value)) return false
      }
    return true
  }

  private fun ViewStateFrame.equalsForTest(right: ViewStateFrame): Boolean {
    val left = this
    if (left.key != right.key) return false
    if (left.viewState != null || right.viewState != null) return false
    val leftBundle = left.androidXBundle ?: return false
    val rightBundle = right.androidXBundle ?: return false
    if (leftBundle.size() != 1 || rightBundle.size() != 1) return false
    return leftBundle.getString(FAKE_STATE_KEY) == rightBundle.getString(FAKE_STATE_KEY)
  }

  data class TestChildState(val state: String) : Parcelable {
    override fun describeContents(): Int = 0
    override fun writeToParcel(
      dest: Parcel,
      flags: Int
    ) {
      dest.writeString(state)
    }

    companion object CREATOR : Parcelable.Creator<TestChildState> {
      override fun createFromParcel(source: Parcel): TestChildState =
        TestChildState(source.readString()!!)

      override fun newArray(size: Int): Array<TestChildState?> = arrayOfNulls(size)
    }
  }

  companion object {
    private const val FAKE_STATE_KEY = "state"
  }
}
