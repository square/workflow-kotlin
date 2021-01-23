package com.squareup.workflow1.ui.backstack.test.fixtures

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
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.backstack.test.fixtures.BackStackContainerLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.backstack.test.fixtures.BackStackContainerLifecycleActivity.TestRendering.RecurseRendering
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.internal.test.AbstractLifecycleTestActivity
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackContainerLifecycleActivity : AbstractLifecycleTestActivity() {

  /**
   * Default rendering always shown in the backstack to simplify test configuration.
   */
  object BaseRendering : ViewFactory<BaseRendering> {
    override val type: KClass<in BaseRendering> = BaseRendering::class
    override fun buildView(
      initialRendering: BaseRendering,
      initialViewEnvironment: ViewEnvironment,
      contextForNewView: Context,
      container: ViewGroup?
    ): View = View(contextForNewView).apply {
      bindShowRendering(initialRendering, initialViewEnvironment) { _, _ -> /* Noop */ }
    }
  }

  sealed class TestRendering {
    data class LeafRendering(val name: String) : TestRendering(), Compatible {
      override val compatibilityKey: String get() = name
    }

    data class RecurseRendering(val wrappedBackstack: List<TestRendering>) : TestRendering()
  }

  override val viewRegistry: ViewRegistry = ViewRegistry(
    NoTransitionBackStackContainer,
    BaseRendering,
    leafViewBinding(LeafRendering::class) { it.name },
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
          stub.update(rendering.wrappedBackstack.toBackstackWithBase(), env)
        }
      }
    },
  )

  fun update(vararg backstack: TestRendering) = update(backstack.asList().toBackstackWithBase())

  private fun List<TestRendering>.toBackstackWithBase() = BackStackScreen(BaseRendering, this)
}
