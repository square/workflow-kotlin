package com.squareup.workflow1.ui

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.container.WrappedScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class)
// SDK 28 required for the four-arg constructor we use in our custom view classes.
@Config(manifest = Config.NONE, sdk = [28])
@OptIn(WorkflowUiExperimentalApi::class, ExperimentalCoroutinesApi::class)
internal class WorkflowLayoutTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  private val workflowLayout = WorkflowLayout(context).apply { id = 42 }

  @Test fun ignoresAlienViewState() {
    var saved = false
    val weirdView = object : View(context) {
      override fun onSaveInstanceState(): Parcelable {
        saved = true
        super.onSaveInstanceState()
        return Bundle()
      }
    }.apply { id = 42 }

    val viewState = SparseArray<Parcelable>()
    weirdView.saveHierarchyState(viewState)
    assertThat(saved).isTrue()

    workflowLayout.restoreHierarchyState(viewState)
    // No crash, no bug.
  }

  @Test fun usesLifecycleDispatcher() {
    val lifecycleDispatcher = UnconfinedTestDispatcher()
    val collectionContext: CoroutineContext = UnconfinedTestDispatcher()
    val testLifecycle = TestLifecycleOwner(
      Lifecycle.State.RESUMED,
      lifecycleDispatcher
    )

    workflowLayout.take(
      lifecycle = testLifecycle.lifecycle,
      renderings = flowOf(WrappedScreen(), WrappedScreen()),
      collectionContext = collectionContext
    )

    // No crash then we safely removed the dispatcher.
  }
}
