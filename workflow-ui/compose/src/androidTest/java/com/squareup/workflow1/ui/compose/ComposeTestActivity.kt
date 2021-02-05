package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.compose.ComposeTestActivity.TestRendering.ComposeRendering
import com.squareup.workflow1.ui.compose.ComposeTestActivity.TestRendering.EmptyRendering
import com.squareup.workflow1.ui.internal.test.AbstractLifecycleTestActivity

@OptIn(WorkflowUiExperimentalApi::class)
internal class ComposeTestActivity : AbstractLifecycleTestActivity() {

  sealed class TestRendering {
    object EmptyRendering : TestRendering()
    data class ComposeRendering(
      val disposeStrategy: ViewCompositionStrategy? = null,
      val content: @Composable () -> Unit
    ) : TestRendering()
  }

  override val viewRegistry: ViewRegistry = ViewRegistry(
    BackStackContainer,
    leafViewBinding(
      ComposeRendering::class,
      viewConstructor = ::ComposeTestView,
      viewObserver = object : ViewObserver<ComposeRendering> {
        override fun onShowRendering(
          view: View,
          rendering: ComposeRendering
        ) {
          if (view !is ComposeTestView) return

          rendering.disposeStrategy?.let {
            // TODO only call this if changed
            view.actualComposeView.setViewCompositionStrategy(it)
          }

          view.actualComposeView.setContent(rendering.content)
        }
      }
    ),
    BuilderViewFactory(EmptyRendering::class)
    { initialRendering, initialViewEnvironment, contextForNewView, _ -> // ktlint-disable curly-spacing
      // Use a ComposeView here because the Compose test infra doesn't like it if there are no
      // Compose views at all. See https://issuetracker.google.com/issues/179455327.
      ComposeView(contextForNewView).apply {
        bindShowRendering(initialRendering, initialViewEnvironment) { _, _ ->
          // Noop.
        }
      }
    }
  )

  fun setBackstack(vararg backstack: TestRendering) {
    update(BackStackScreen(EmptyRendering, backstack.asList()))
  }

  /**
   * This view must inherit [LeafView] to work with [AbstractLifecycleTestActivity], so the actual
   * Compose view is a child.
   */
  class ComposeTestView(context: Context) : LeafView<ComposeRendering>(context) {
    val actualComposeView = ComposeView(context)

    init {
      addView(actualComposeView)
    }
  }
}
