package com.squareup.workflow1.ui.backstack.test.fixtures

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
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
import com.squareup.workflow1.ui.internal.test.ViewStateTestView
import com.squareup.workflow1.ui.internal.test.inAnyView
import org.hamcrest.Matcher
import org.hamcrest.Matchers.equalTo
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

  private val viewObserver =
    object : ViewObserver<LeafRendering> by lifecycleLoggingViewObserver() {
      override fun onViewCreated(
        view: View,
        rendering: LeafRendering
      ) {
        view.tag = rendering.name

        // Need to set the view to enable view persistence.
        view.id = rendering.name.hashCode()

        logEvent("${rendering.name} onViewCreated viewState=${view.viewState}")
      }

      override fun onShowRendering(
        view: View,
        rendering: LeafRendering
      ) {
        check(view.tag == rendering.name)
        logEvent("${rendering.name} onShowRendering viewState=${view.viewState}")
      }

      override fun onAttachedToWindow(
        view: View,
        rendering: LeafRendering
      ) {
        logEvent("${rendering.name} onAttach viewState=${view.viewState}")
      }

      override fun onDetachedFromWindow(
        view: View,
        rendering: LeafRendering
      ) {
        logEvent("${rendering.name} onDetach viewState=${view.viewState}")
      }

      override fun onSaveInstanceState(
        view: View,
        rendering: LeafRendering
      ) {
        logEvent("${rendering.name} onSave viewState=${view.viewState}")
      }

      override fun onRestoreInstanceState(
        view: View,
        rendering: LeafRendering
      ) {
        logEvent("${rendering.name} onRestore viewState=${view.viewState}")
      }

      @Suppress("UNCHECKED_CAST")
      private val View.viewState
        get() = (this as ViewStateTestView<LeafRendering>).viewState
    }

  override val viewRegistry: ViewRegistry = ViewRegistry(
    NoTransitionBackStackContainer,
    BaseRendering,
    leafViewBinding(
      LeafRendering::class,
      viewObserver,
      viewConstructor = ::ViewStateTestView
    ),
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

  /** Returns the view that is the current screen. */
  val currentTestView: ViewStateTestView<LeafRendering>
    get() {
      val backstackContainer = rootRenderedView as ViewGroup
      check(backstackContainer.childCount == 1)
      @Suppress("UNCHECKED_CAST")
      return backstackContainer.getChildAt(0) as ViewStateTestView<LeafRendering>
    }

  fun update(vararg backstack: TestRendering) =
    setRendering(backstack.asList().toBackstackWithBase())

  private fun List<TestRendering>.toBackstackWithBase() =
    BackStackScreen(BaseRendering, this)
}

internal fun ActivityScenario<BackStackContainerLifecycleActivity>.viewForScreen(
  name: String
): ViewStateTestView<LeafRendering> {
  waitForScreen(name)
  lateinit var view: ViewStateTestView<LeafRendering>
  onActivity {
    view = it.currentTestView
  }
  return view
}

internal fun waitForScreen(name: String) {
 inAnyView(withTagValue(equalTo(name)) as Matcher<View>)
    .check(matches(isCompletelyDisplayed()))
}
