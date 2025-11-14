package com.squareup.workflow1.ui

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented tests for [WorkflowLayout] that require a real Android environment.
 * These tests verify behavior that cannot be properly tested with Robolectric,
 * such as the main thread requirement for collecting renderings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkflowLayoutInstrumentedTest {

  @Test
  fun throwsWhenCollectingOnBackgroundThread() {

    var exception: Throwable? = null
    val countDownLatch = CountDownLatch(1)

    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
      exception = throwable
      countDownLatch.countDown()
    }

    val testLifeCycleOwner = TestLifecycleOwner(Lifecycle.State.CREATED)
    val renderings = MutableStateFlow(TestScreen())

    val nonMainThreadDispatcher = Dispatchers.IO

    val workflowLayout = WorkflowLayout(InstrumentationRegistry.getInstrumentation().context)
    workflowLayout.take(
      lifecycle = testLifeCycleOwner.lifecycle,
      renderings = renderings,
      collectionContext = nonMainThreadDispatcher
    )

    // start the lifecycle.
    testLifeCycleOwner.lifecycle.currentState = Lifecycle.State.STARTED

    countDownLatch.await(1000, TimeUnit.MILLISECONDS)

    assertThat(exception).isNotNull()
    assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(exception?.message).contains("Collection dispatch must happen on the main thread!")
  }

  /**
   * Simple test screen for instrumented tests.
   */
  private class TestScreen : AndroidScreen<TestScreen> {
    override val viewFactory =
      ScreenViewFactory.fromCode<TestScreen> { _, initialEnvironment, context, _ ->
        ScreenViewHolder(initialEnvironment, View(context)) { _, _ -> }
      }
  }
}
