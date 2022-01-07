package com.squareup.workflow1.ui

import android.widget.FrameLayout
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
    leafViewBinding<LeafRendering>(lifecycleLoggingViewObserver { it.name }),
    ScreenViewFactory.of<RecurseRendering> { initialRendering,
      initialViewEnvironment,
      contextForNewView, _ ->
      FrameLayout(contextForNewView).let { container ->
        val stub = WorkflowViewStub(contextForNewView)
        container.addView(stub)
        ScreenViewHolder(
          initialRendering,
          initialViewEnvironment,
          container
        ) { rendering, env ->
          stub.show(rendering.wrapped, env)
        }
      }
    },
  )

  fun update(rendering: TestRendering) = super.setRendering(rendering)
}
