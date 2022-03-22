package com.squareup.workflow1.ui.container.fixtures

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.forBuiltView
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.fixtures.BackStackContainerLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.container.fixtures.BackStackContainerLifecycleActivity.TestRendering.OuterRendering
import com.squareup.workflow1.ui.container.fixtures.BackStackContainerLifecycleActivity.TestRendering.RecurseRendering
import com.squareup.workflow1.ui.internal.test.AbstractLifecycleTestActivity
import com.squareup.workflow1.ui.internal.test.inAnyView
import org.hamcrest.Matcher
import org.hamcrest.Matchers.equalTo
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackContainerLifecycleActivity : AbstractLifecycleTestActivity() {

  /**
   * Default rendering always shown in the backstack to simplify test configuration.
   */
  object BaseRendering :
    Screen,
    ScreenViewFactory<BaseRendering> by ScreenViewFactory(
      buildView = { _, context, _ -> View(context) },
      updateView = { _, _, _ -> /* Noop */ }
    ) {
    override val type: KClass<in BaseRendering> = BaseRendering::class
  }

  sealed class TestRendering : Screen {
    data class LeafRendering(val name: String) : TestRendering(), Compatible {
      override val compatibilityKey: String get() = name
    }

    data class RecurseRendering(val wrappedBackstack: List<TestRendering>) : TestRendering()

    @OptIn(WorkflowUiExperimentalApi::class)
    data class OuterRendering(val name: String) : TestRendering() {
      val backStack = BackStackScreen(LeafRendering("nested leaf in $name"))
    }
  }

  private val viewObserver =
    object : ViewObserver<LeafRendering> by lifecycleLoggingViewObserver({ it.name }) {
      override fun onViewCreated(
        view: View
      ) {
        logEvent("onViewCreated viewState=${view.viewState}")
      }

      override fun onShowRendering(
        view: View,
        rendering: LeafRendering
      ) {
        if (view.tag == null) {
          view.tag = rendering.name

          // Need to set the view to enable view persistence.
          view.id = rendering.name.hashCode()
        }

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

      private val View.viewState get() = (this as ViewStateTestView).viewState
    }

  override val viewRegistry: ViewRegistry = ViewRegistry(
    NoTransitionBackStackContainer,
    BaseRendering,
    leafViewBinding(LeafRendering::class, viewObserver, viewConstructor = ::ViewStateTestView),
    forBuiltView<RecurseRendering> { _, context, _ ->
      val stub = WorkflowViewStub(context)
      val frame = FrameLayout(context).also { container ->
        container.addView(stub)
      }
      val runner = ScreenViewRunner<RecurseRendering> { rendering, viewEnvironment ->
        stub.show(rendering.wrappedBackstack.toBackstackWithBase(), viewEnvironment)
      }
      Pair(frame, runner)
    },
    forBuiltView<OuterRendering> { _, context, _ ->
      val stub = WorkflowViewStub(context)
      val frame = FrameLayout(context).also { container ->
        container.addView(stub)
      }
      val runner = ScreenViewRunner<OuterRendering> { rendering, viewEnvironment ->
        stub.show(rendering.backStack, viewEnvironment)
      }
      Pair(frame, runner)
    }
  )

  /** Returns the view that is the current screen. */
  val currentTestView: ViewStateTestView
    get() {
      val backstackContainer = rootRenderedView as ViewGroup
      check(backstackContainer.childCount == 1)
      return backstackContainer.getChildAt(0) as ViewStateTestView
    }

  fun update(vararg backstack: TestRendering) =
    setRendering(backstack.asList().toBackstackWithBase())

  private fun List<TestRendering>.toBackstackWithBase() =
    BackStackScreen(BaseRendering, this)
}

internal fun ActivityScenario<BackStackContainerLifecycleActivity>.viewForScreen(
  name: String
): ViewStateTestView {
  waitForScreen(name)
  lateinit var view: ViewStateTestView
  onActivity {
    view = it.currentTestView
  }
  return view
}

@OptIn(WorkflowUiExperimentalApi::class)
internal fun waitForScreen(name: String) {
  inAnyView(withTagValue(equalTo(name)) as Matcher<View>)
    .check(matches(isCompletelyDisplayed()))
}
