package com.squareup.workflow1.ui

import android.content.res.Resources.NotFoundException
import android.view.View
import android.view.View.OnAttachStateChangeListener
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.verify
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class, WorkflowUiExperimentalApi::class)
internal class ViewLaunchWhenAttachedTest {

  private val view = mockView()
  private val onAttachStateChangeListener = argumentCaptor<OnAttachStateChangeListener>()

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test fun `launchWhenAttached launches synchronously when already attached`() = runTest {
    var innerJob: Job? = null
    var started = false
    mockAttachedToWindow(view, true)

    // Action: launch a coroutine!
    view.launchWhenAttached {
      started = true
      suspendCancellableCoroutine { continuation ->
        innerJob = continuation.context.job
      }
    }

    assertThat(started).isTrue()
    assertThat(innerJob).isNotNull()

    // Grab the attach listener so we can detach it.
    verify(view).setTag(isA(), isA<OnAttachStateChangeListener>())

    // Action: detach view!
    performViewDetach()
    assertThat(innerJob!!.isCancelled).isTrue()
  }

  @Test fun `launchWhenAttached cancels when detached while launching`() = runTest {
    mockAttachedToWindow(view, true)

    // Action: launch a coroutine!
    view.launchWhenAttached {
      val innerJob = coroutineContext.job

      // Detach view without suspending:
      performViewDetach()
      assertThat(innerJob.isCancelled).isTrue()
    }
  }

  @Test fun `launchWhenAttached launches when attached later`() = runTest {
    var innerJob: Job? = null
    mockAttachedToWindow(view, false)

    // Action: launch coroutine!
    view.launchWhenAttached {
      suspendCancellableCoroutine { continuation ->
        innerJob = continuation.context.job
      }
    }

    testScheduler.advanceUntilIdle()
    assertThat(innerJob).isNull()

    verify(view).setTag(isA(), isA<OnAttachStateChangeListener>())

    // Action: attach view!
    performViewAttach()
    testScheduler.advanceUntilIdle()
    assertThat(innerJob).isNotNull()
    assertThat(innerJob!!.isActive).isTrue()

    // Action: detach view!
    performViewDetach()
    assertThat(innerJob!!.isCancelled).isTrue()
  }

  @Test fun `launchWhenAttached launches when reattached`() = runTest {
    var innerJob: Job? = null
    mockAttachedToWindow(view, true)

    // Action: launch first coroutine and immediately detach.
    view.launchWhenAttached {}
    performViewDetach()

    // Action: launch second coroutine!
    view.launchWhenAttached {
      suspendCancellableCoroutine { continuation ->
        innerJob = continuation.context.job
      }
    }

    // The coroutine shouldn't have started since the view is detached.
    testScheduler.advanceUntilIdle()
    assertThat(innerJob).isNull()

    // Action: re-attach view!
    val secondAttachListener = argumentCaptor<OnAttachStateChangeListener>()
    verify(view, times(2)).addOnAttachStateChangeListener(secondAttachListener.capture())
    assertThat(secondAttachListener.secondValue).isNotNull()
    secondAttachListener.secondValue.onViewAttachedToWindow(view)

    assertThat(innerJob).isNotNull()
    assertThat(innerJob!!.isActive).isTrue()
  }

  @Test fun `launchWhenAttached coroutine is child of ViewTreeLifecycleOwner`() = runTest {
    var innerJob: Job? = null
    mockAttachedToWindow(view, true)

    // Action: launch coroutine!
    view.launchWhenAttached {
      suspendCancellableCoroutine { continuation ->
        innerJob = continuation.context.job
      }
    }
    testScheduler.advanceUntilIdle()

    assertThat(innerJob!!.isActive).isTrue()

    // Action: cancel parent scope!
    (ViewTreeLifecycleOwner.get(view) as TestLifecycleOwner)
      .handleLifecycleEvent(ON_DESTROY)

    assertThat(innerJob!!.isCancelled).isTrue()
  }

  @Test fun `launchWhenAttached includes view classname in coroutine name`() = runTest {
    var coroutineName: String? = null
    mockAttachedToWindow(view, true)

    // Action: launch coroutine!
    view.launchWhenAttached {
      coroutineName = coroutineContext[CoroutineName]?.name
    }

    assertThat(coroutineName).isNotNull()
    assertThat(coroutineName).contains("android.view.View")
    assertThat(coroutineName).contains("${view.hashCode()}")
  }

  @Test fun `launchWhenAttached includes view id name in coroutine name`() = runTest {
    var coroutineName: String? = null
    mockAttachedToWindow(view, true)
    whenever(view.resources.getResourceEntryName(anyInt())).thenReturn("fnord")

    // Action: launch coroutine!
    view.launchWhenAttached {
      coroutineName = coroutineContext[CoroutineName]?.name
    }

    assertThat(coroutineName).contains("fnord")
  }

  @Test fun `launchWhenAttached tolerates garbage ids`() = runTest {
    var coroutineName: String? = null
    mockAttachedToWindow(view, true)
    whenever(view.resources.getResourceEntryName(anyInt())).thenThrow(NotFoundException())

    // Action: launch coroutine!
    view.launchWhenAttached {
      coroutineName = coroutineContext[CoroutineName]?.name
    }

    assertThat(coroutineName).isNotNull()
    assertThat(coroutineName).contains("android.view.View")
    assertThat(coroutineName).contains("${view.hashCode()}")
  }

  private fun performViewAttach() {
    mockAttachedToWindow(view, true)
    verify(view).addOnAttachStateChangeListener(onAttachStateChangeListener.capture())
    assertThat(onAttachStateChangeListener.firstValue).isNotNull()
    onAttachStateChangeListener.firstValue.onViewAttachedToWindow(view)
  }

  private fun performViewDetach() {
    mockAttachedToWindow(view, false)
    verify(view).addOnAttachStateChangeListener(onAttachStateChangeListener.capture())
    assertThat(onAttachStateChangeListener.firstValue).isNotNull()
    onAttachStateChangeListener.firstValue.onViewDetachedFromWindow(view)
  }

  private fun mockView(): View {
    return mock<View>(defaultAnswer = RETURNS_DEEP_STUBS).also {
      mockTags(it)
      mockAttachedToWindow(it)
      ViewTreeLifecycleOwner.set(
        it,
        TestLifecycleOwner(coroutineDispatcher = UnconfinedTestDispatcher())
      )
    }
  }

  private fun mockAttachedToWindow(
    mockView: View,
    attached: Boolean
  ) {
    whenever(mockView.isAttachedToWindow).thenReturn(attached)
  }

  private fun mockTags(mockView: View) {
    // Implement "tags" on the mocked view, backed by an actual map.
    val tags: MutableMap<Int, Any> = LinkedHashMap()

    whenever(mockView.getTag(anyInt()))
      .thenAnswer { invocation: InvocationOnMock ->
        val key = invocation.arguments[0] as Int
        tags[key]
      }

    doAnswer { invocation: InvocationOnMock ->
      val key = invocation.arguments[0] as Int
      tags[key] = invocation.arguments[1]
      null
    }.whenever(mockView).setTag(anyInt(), any())
  }

  private fun mockAttachedToWindow(mockView: View) {
    whenever(mockView.isAttachedToWindow).thenReturn(true)

    // This makes ViewCompat#isAttachedToWindow return true. See RxViews#unsubscribeOnDetach.
    whenever(mockView.windowToken).thenReturn(mock())
  }
}
