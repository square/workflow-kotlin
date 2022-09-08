package com.squareup.workflow1.ui.container

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Parcel
import android.os.Parcelable
import android.util.SparseArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.container.fixtures.ViewStateTestView
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.visual.VisualEnvironment.Companion.EMPTY
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
internal class ViewStateCacheTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val viewEnvironment = EMPTY

  private object AScreen : Screen

  @Test fun saves_and_restores_self() {
    val rendering = NamedScreen(wrapped = AScreen, name = "rendering")
    val childState = SparseArray<Parcelable>().apply {
      put(0, TestChildState("hello world"))
    }
    val cache = ViewStateCache(
      viewStates = mutableMapOf(
        rendering.name to ViewStateFrame(rendering.name, childState)
      )
    )
    val parcel = Parcel.obtain()

    parcel.writeParcelable(cache.save(), 0)

    parcel.setDataPosition(0)
    val restoredCache = (
      if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
        parcel.readParcelable(
          ViewStateCache.Saved::class.java.classLoader,
          ViewStateCache.Saved::class.java
        )!!
      } else {
        @Suppress("DEPRECATION")
        parcel.readParcelable(ViewStateCache.Saved::class.java.classLoader)!!
      }
      ).let { restoredState ->
      ViewStateCache().apply { restore(restoredState) }
    }

    assertThat(restoredCache.equalsForTest(cache)).isTrue()
  }

  @Test fun saves_and_restores_child_states_on_navigation() {
    val cache = ViewStateCache()
    val firstRendering = NamedScreen(wrapped = AScreen, name = "first")
    val secondRendering = NamedScreen(wrapped = AScreen, name = "second")
    // Android requires ID to be set for view hierarchy to be saved or restored.
    val firstView = createTestView(firstRendering, id = 1)
    val secondView = createTestView(secondRendering)

    // Set some state on the first view that will be saved.
    firstView.viewState = "hello world"

    // Show the first screen.
    cache.update(retainedRenderings = emptyList(), oldHolderMaybe = null, newHolder = firstView)

    // "Navigate" to the second screen, saving the first screen.
    cache.update(
      retainedRenderings = listOf(firstRendering),
      oldHolderMaybe = firstView,
      newHolder = secondView
    )

    // Nothing should read this value again, but clear it to make sure.
    firstView.viewState = "ignored"

    // "Navigate" back to the first screen, restoring state.
    val firstViewRestored = createTestView(firstRendering, id = 1)
    cache.update(listOf(), oldHolderMaybe = secondView, newHolder = firstViewRestored)

    // Check that the state was restored.
    assertThat(firstViewRestored.viewState).isEqualTo("hello world")
  }

  @Test fun doesnt_restore_state_when_restored_view_id_is_different() {
    val cache = ViewStateCache()
    val firstRendering = NamedScreen(wrapped = AScreen, name = "first")
    val secondRendering = NamedScreen(wrapped = AScreen, name = "second")
    // Android requires ID to be set for view hierarchy to be saved or restored.
    val firstView = createTestView(firstRendering, id = 1)
    val secondView = createTestView(secondRendering)

    // Show the first screen.
    cache.update(retainedRenderings = emptyList(), oldHolderMaybe = null, newHolder = firstView)

    // Set some state on the first view that will be saved.
    firstView.viewState = "hello world"

    // "Navigate" to the second screen, saving the first screen.
    cache.update(
      retainedRenderings = listOf(firstRendering),
      oldHolderMaybe = firstView,
      newHolder = secondView
    )

    // Nothing should read this value again, but clear it to make sure.
    firstView.viewState = "ignored"

    // "Navigate" back to the first screen, restoring state.
    val firstViewRestored = ViewStateTestView(instrumentation.context).apply {
      id = 2
      WorkflowLifecycleOwner.installOn(this)
    }
    val firstHolderRestored =
      ScreenViewHolder<NamedScreen<*>>(EMPTY, firstViewRestored) { _, _ -> }.also {
        it.show(firstRendering, viewEnvironment)
      }
    cache.update(
      listOf(firstRendering),
      oldHolderMaybe = secondView,
      newHolder = firstHolderRestored
    )

    // Check that the state was restored.
    assertThat(firstViewRestored.viewState).isEqualTo("")
  }

  @Test fun doesnt_restore_state_when_view_id_not_set() {
    val cache = ViewStateCache()
    val firstRendering = NamedScreen(wrapped = AScreen, name = "first")
    val secondRendering = NamedScreen(wrapped = AScreen, name = "second")
    val firstView = createTestView(firstRendering)
    val secondView = createTestView(secondRendering)

    // Set some state on the first view that will be saved.
    firstView.viewState = "hello world"

    // Show the first screen.
    cache.update(retainedRenderings = emptyList(), oldHolderMaybe = null, newHolder = firstView)

    // "Navigate" to the second screen, saving the first screen.
    cache.update(listOf(firstRendering), oldHolderMaybe = firstView, newHolder = secondView)

    // Nothing should read this value again, but clear it to make sure.
    firstView.viewState = "ignored"

    // "Navigate" back to the first screen, restoring state.
    val firstViewRestored = createTestView(firstRendering)
    cache.update(listOf(firstRendering), oldHolderMaybe = secondView, newHolder = firstViewRestored)

    // Check that the state was NOT restored.
    assertThat(firstViewRestored.viewState).isEqualTo("")
  }

  @Test fun throws_on_duplicate_renderings() {
    val cache = ViewStateCache()
    val rendering = NamedScreen(wrapped = AScreen, name = "duplicate")
    val view = createTestView(rendering)

    try {
      cache.update(listOf(rendering, rendering), null, view)
      fail("Expected exception.")
    } catch (e: IllegalArgumentException) {
      assertThat(e.message).contains("Duplicate entries not allowed")
    }
  }

  private val ScreenViewHolder<*>.testView get() = (view as ViewStateTestView)
  private var ScreenViewHolder<*>.viewState: String
    get() = testView.viewState
    set(value) {
      testView.viewState = value
    }

  private fun createTestView(
    firstRendering: NamedScreen<*>,
    id: Int? = null
  ): ScreenViewHolder<NamedScreen<*>> {
    val view = ViewStateTestView(instrumentation.context).also { view ->
      id?.let { view.id = id }
      WorkflowLifecycleOwner.installOn(view)
    }
    return ScreenViewHolder<NamedScreen<*>>(EMPTY, view) { _, _ -> }.also {
      it.show(firstRendering, viewEnvironment)
    }
  }

  private fun ViewStateCache.equalsForTest(other: ViewStateCache): Boolean {
    if (viewStates.size != other.viewStates.size) return false
    viewStates.entries.sortedBy { it.key }
      .zip(other.viewStates.entries.sortedBy { it.key })
      .forEach { (leftEntry, rightEntry) ->
        if (leftEntry.key != rightEntry.key) return false
        if (!leftEntry.value.equalsForTest(rightEntry.value)) return false
      }
    return true
  }

  private fun ViewStateFrame.equalsForTest(other: ViewStateFrame): Boolean {
    return key == other.key && viewState.toMap() == other.viewState.toMap()
  }

  private fun <T> SparseArray<T>.toMap(): Map<Int, T> =
    (0 until size()).associate { i -> keyAt(i).let { it to get(it) } }

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
}
