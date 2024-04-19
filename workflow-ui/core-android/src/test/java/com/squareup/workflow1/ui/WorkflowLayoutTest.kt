package com.squareup.workflow1.ui

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.androidx.OnBackPressedDispatcherOwnerKey
import com.squareup.workflow1.ui.navigation.WrappedScreen
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
    val weirdView = BundleSavingView(context)

    val viewState = SparseArray<Parcelable>()
    weirdView.saveHierarchyState(viewState)
    assertThat(weirdView.saved).isTrue()

    workflowLayout.restoreHierarchyState(viewState)
    // No crash, no bug.
  }

  @Test fun ignoresNestedViewStateMistakes() {
    class AScreen : AndroidScreen<AScreen> {
      override val viewFactory =
        ScreenViewFactory.fromCode<AScreen> { _, initialEnvironment, context, _ ->
          ScreenViewHolder(initialEnvironment, BundleSavingView(context)) { _, _ -> }
        }
    }

    class BScreen : AndroidScreen<BScreen> {
      override val viewFactory =
        ScreenViewFactory.fromCode<BScreen> { _, initialEnvironment, context, _ ->
          ScreenViewHolder(initialEnvironment, SaveStateSavingView(context)) { _, _ -> }
        }
    }

    val onBack = object : OnBackPressedDispatcherOwner {
      override fun getOnBackPressedDispatcher() = error("nope")
      override val lifecycle: Lifecycle get() = error("also nope")
    }

    val env = ViewEnvironment.EMPTY + (OnBackPressedDispatcherOwnerKey to onBack)

    val original = WorkflowLayout(context).apply { id = 1 }
    original.show(AScreen(), env)

    val viewState = SparseArray<Parcelable>()
    original.saveHierarchyState(viewState)

    val unoriginal = WorkflowLayout(context).apply { id = 1 }
    unoriginal.restoreHierarchyState(viewState)
    unoriginal.show(BScreen(), env)
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

  private class BundleSavingView(context: Context) : View(context) {
    var saved = false

    init {
      id = 42
    }

    override fun onSaveInstanceState(): Parcelable {
      saved = true
      super.onSaveInstanceState()
      return Bundle()
    }
  }

  private class SaveStateSavingView(context: Context) : View(context) {
    init {
      id = 42
    }
  }
}
