package com.squareup.workflow1.ui

import android.os.Bundle
import android.widget.FrameLayout
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.WorkflowViewStubLifecycleActivity.TestRendering.RecurseRendering
import com.squareup.workflow1.ui.internal.test.AbstractLifecycleTestActivity

@OptIn(WorkflowUiExperimentalApi::class)
internal class WorkflowViewStubLifecycleActivity : AbstractLifecycleTestActivity() {

  sealed class TestRendering {
    data class LeafRendering(val name: String) : TestRendering(), Compatible {
      override val compatibilityKey: String get() = name
    }

    data class RecurseRendering(val wrapped: LeafRendering) : TestRendering()
  }

  override val viewRegistry: ViewRegistry = ViewRegistry(
    leafViewBinding(LeafRendering::class, lifecycleLoggingViewObserver { it.name }),
    BuilderViewFactory(RecurseRendering::class) { initialRendering,
      initialViewEnvironment,
      contextForNewView, _ ->
      FrameLayout(contextForNewView).also { container ->
        val stub = WorkflowViewStub(contextForNewView)
        container.addView(stub)
        container.bindShowRendering(
          initialRendering,
          initialViewEnvironment
        ) { rendering, env ->
          stub.update(rendering.wrapped, env)
        }
      }
    },
  )

  fun update(rendering: TestRendering) = super.update(rendering)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    update(LeafRendering("initial"))
  }
}
