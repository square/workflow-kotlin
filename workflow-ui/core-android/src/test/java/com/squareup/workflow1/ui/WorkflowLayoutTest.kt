package com.squareup.workflow1.ui

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.core.view.get
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.androidx.OnBackPressedDispatcherOwnerKey
import com.squareup.workflow1.ui.navigation.WrappedScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@RunWith(RobolectricTestRunner::class)
// SDK 28 required for the four-arg constructor we use in our custom view classes.
@Config(manifest = Config.NONE, sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkflowLayoutTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  private val workflowLayout = WorkflowLayout(context).apply {
    id = 42
    setViewTreeOnBackPressedDispatcherOwner(object : OnBackPressedDispatcherOwner {
      override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() {
          error("yeah no")
        }

      override val lifecycle: Lifecycle get() = error("nope")
    })
  }

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
      override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() {
          error("nope")
        }
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

  @Test fun takes() {
    val lifecycleDispatcher = StandardTestDispatcher()
    val testLifecycle = TestLifecycleOwner(
      initialState = Lifecycle.State.RESUMED,
      coroutineDispatcher = lifecycleDispatcher
    )
    val flow = MutableSharedFlow<Screen>()

    runTest(lifecycleDispatcher) {
      workflowLayout.take(
        lifecycle = testLifecycle.lifecycle,
        renderings = flow,
      )
      // Start the collector coroutine so it subscribes to the SharedFlow before we emit.
      advanceUntilIdle()
      assertThat(workflowLayout[0]).isInstanceOf(WorkflowViewStub::class.java)

      flow.emit(WrappedScreen())
      advanceUntilIdle()
      assertThat(workflowLayout[0]).isNotInstanceOf(WorkflowViewStub::class.java)
    }
  }

  @Test fun canStopTaking() {
    val lifecycleDispatcher = StandardTestDispatcher()
    val testLifecycle = TestLifecycleOwner(
      initialState = Lifecycle.State.RESUMED,
      coroutineDispatcher = lifecycleDispatcher
    )
    val flow = MutableSharedFlow<Screen>()

    runTest(lifecycleDispatcher) {
      val job = workflowLayout.take(
        lifecycle = testLifecycle.lifecycle,
        renderings = flow,
      )
      // Start the collector, then cancel and process cancellation so it unsubscribes from the
      // SharedFlow. Without this, emit() would deadlock waiting for the cancelled subscriber.
      advanceUntilIdle()
      job.cancel()
      advanceUntilIdle()
      flow.emit(WrappedScreen())
      // Technically not needed since emit returns right away, but included so we know that
      // we tried to take anything emitted.
      advanceUntilIdle()
      assertThat(workflowLayout[0]).isInstanceOf(WorkflowViewStub::class.java)
    }
  }

  @Test fun usesProvidedCoroutineContext() {
    val lifecycleDispatcher = StandardTestDispatcher()
    val testLifecycle = TestLifecycleOwner(
      initialState = Lifecycle.State.RESUMED,
      coroutineDispatcher = lifecycleDispatcher
    )
    val flow = MutableSharedFlow<Screen>()

    val testElement = TestContextElement()

    val trackedFlow = flow.onEach {
      if (coroutineContext[TestContextElement] != null) {
        testElement.wasUsed = true
      }
    }

    runTest(lifecycleDispatcher) {
      workflowLayout.take(
        lifecycle = testLifecycle.lifecycle,
        renderings = trackedFlow,
        collectionContext = testElement
      )
      // Start the collector coroutine so it subscribes to the SharedFlow before we emit.
      advanceUntilIdle()

      flow.emit(WrappedScreen())
      advanceUntilIdle()

      assertThat(testElement.wasUsed).isTrue()
    }
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

  private class TestContextElement : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TestContextElement>

    @Volatile
    var wasUsed = false
  }
}
