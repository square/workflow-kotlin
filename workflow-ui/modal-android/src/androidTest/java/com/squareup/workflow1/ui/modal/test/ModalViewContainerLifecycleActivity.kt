package com.squareup.workflow1.ui.modal.test

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.internal.test.AbstractLifecycleTestActivity
import com.squareup.workflow1.ui.modal.HasModals
import com.squareup.workflow1.ui.modal.ModalViewContainer
import com.squareup.workflow1.ui.modal.test.ModalViewContainerLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.modal.test.ModalViewContainerLifecycleActivity.TestRendering.RecurseRendering
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
internal class ModalViewContainerLifecycleActivity : AbstractLifecycleTestActivity() {

  object BaseRendering : ViewFactory<BaseRendering> {
    override val type: KClass<in BaseRendering> = BaseRendering::class
    override fun buildView(
      initialRendering: BaseRendering,
      initialViewEnvironment: ViewEnvironment,
      contextForNewView: Context,
      container: ViewGroup?
    ): View = View(contextForNewView).apply {
      bindShowRendering() { _, _ -> /* Noop */ }
    }
  }

  data class TestModals(
    override val modals: List<TestRendering>
  ) : HasModals<BaseRendering, TestRendering> {
    override val beneathModals: BaseRendering get() = BaseRendering
  }

  sealed class TestRendering {
    data class LeafRendering(val name: String) : TestRendering(), Compatible {
      override val compatibilityKey: String get() = name
    }

    data class RecurseRendering(val wrapped: LeafRendering) : TestRendering()
  }

  override val viewRegistry: ViewRegistry = ViewRegistry(
    ModalViewContainer.binding<TestModals>(),
    BaseRendering,
    leafViewBinding(LeafRendering::class, lifecycleLoggingViewObserver { it.name }),
    BuilderViewFactory(RecurseRendering::class) { initialRendering,
      initialViewEnvironment,
      contextForNewView, _ ->
      FrameLayout(contextForNewView).also { container ->
        val stub = WorkflowViewStub(contextForNewView)
        container.addView(stub)
        container.bindShowRendering() { rendering, env ->
          stub.update(TestModals(listOf(rendering.wrapped)), env)
        }
      }
    },
  )

  fun update(vararg modals: TestRendering) = setRendering(TestModals(modals.asList()))
}
