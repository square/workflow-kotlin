@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.modal.test

import android.view.View
import android.widget.FrameLayout
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromCode
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.asScreen
import com.squareup.workflow1.ui.internal.test.AbstractLifecycleTestActivity
import com.squareup.workflow1.ui.modal.HasModals
import com.squareup.workflow1.ui.modal.ModalViewContainer
import com.squareup.workflow1.ui.modal.test.ModalViewContainerLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.modal.test.ModalViewContainerLifecycleActivity.TestRendering.RecurseRendering

@OptIn(WorkflowUiExperimentalApi::class)
internal class ModalViewContainerLifecycleActivity : AbstractLifecycleTestActivity() {

  object BaseRendering :
    Screen,
    ScreenViewFactory<BaseRendering> by ScreenViewFactory.fromCode(
      buildView = { _, environment, context, _ ->
        ScreenViewHolder(environment, View(context)) { _, _ -> }
      }
    )

  data class TestModals(
    override val modals: List<TestRendering>
  ) : HasModals<BaseRendering, TestRendering> {
    override val beneathModals: BaseRendering get() = BaseRendering
  }

  sealed class TestRendering : Screen {
    data class LeafRendering(val name: String) : TestRendering(), Compatible {
      override val compatibilityKey: String get() = name
    }

    data class RecurseRendering(val wrapped: LeafRendering) : TestRendering()
  }

  override val viewRegistry: ViewRegistry = ViewRegistry(
    ModalViewContainer.binding<TestModals>(),
    BaseRendering,
    leafViewBinding(LeafRendering::class, lifecycleLoggingViewObserver { it.name }),
    fromCode<RecurseRendering> { _, environment, context, _ ->
      val stub = WorkflowViewStub(context)
      val frame = FrameLayout(context).also { container ->
        container.addView(stub)
      }
      ScreenViewHolder(environment, frame) { rendering, viewEnvironment ->
        stub.show(asScreen(TestModals(listOf(rendering.wrapped))), viewEnvironment)
      }
    }
  )

  fun update(vararg modals: TestRendering) =
    setRendering(asScreen(TestModals(modals.asList())))
}
