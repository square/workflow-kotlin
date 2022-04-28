package com.squareup.workflow1.ui

import android.widget.FrameLayout
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromCode
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.RecurseRendering
import com.squareup.workflow1.ui.internal.test.AbstractLifecycleTestActivity

@OptIn(WorkflowUiExperimentalApi::class)
internal class WorkflowViewStubLifecycleActivity : AbstractLifecycleTestActivity() {

  sealed class TestRendering : Screen {
    data class LeafRendering(val name: String) : TestRendering(), Compatible {
      override val compatibilityKey: String get() = name
    }

    data class RecurseRendering(val wrapped: TestRendering) : TestRendering()

    abstract class ViewRendering<T : ViewRendering<T>> : TestRendering(), AndroidScreen<T>
  }

  override val viewRegistry: ViewRegistry = ViewRegistry(
    leafViewBinding(LeafRendering::class, lifecycleLoggingViewObserver { it.name }),
    fromCode<RecurseRendering> { _, initialEnvironment, context, _ ->
      val stub = WorkflowViewStub(context)
      val frame = FrameLayout(context)
      frame.addView(stub)
      ScreenViewHolder(initialEnvironment, frame) { rendering, viewEnvironment ->
        stub.show(rendering.wrapped, viewEnvironment)
      }
    }
  )

  fun update(rendering: TestRendering) = super.setRendering(rendering)
}
