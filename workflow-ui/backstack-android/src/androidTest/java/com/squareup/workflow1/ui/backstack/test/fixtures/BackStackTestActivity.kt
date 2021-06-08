package com.squareup.workflow1.ui.backstack.test.fixtures

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.NamedViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackContainer
import com.squareup.workflow1.ui.backstack.BackStackScreen
import com.squareup.workflow1.ui.backstack.test.fixtures.BackStackTestActivity.TestRendering
import com.squareup.workflow1.ui.backstack.test.fixtures.ViewStateTestView.ViewHooks
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.showRendering

/**
 * Basic activity that only contains a [BackStackContainer] for [BackStackContainerTest].
 * You can "navigate" by setting the [backstack] property. The backstack consists of named
 * [TestRendering] objects. The backstack value will be preserved across configuration changes via
 * [onRetainNonConfigurationInstance].
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal class BackStackTestActivity : Activity() {

  /**
   * A simple string holder that creates [ViewStateTestView]s with their ID and tag derived from
   * [name]. This rendering implements [Compatible] and is keyed off [name], so that renderings with
   * different names will cause new views to be created.
   *
   * @param onViewCreated An optional function that will be called by the view factory after the
   * view is created but before [bindShowRendering].
   */
  internal class TestRendering(
    val name: String,
    val onViewCreated: (ViewStateTestView) -> Unit = {},
    val onShowRendering: (ViewStateTestView) -> Unit = {},
    val viewHooks: ViewHooks? = null
  ) : Compatible {
    override val compatibilityKey: String = name

    /**
     * Creates [ViewStateTestView]s with the following attributes:
     * - [id][View.getId] is set to the hashcode of [name].
     * - [tag][View.getTag] is set to [name] for easy Espresso matching.
     */
    companion object : ViewFactory<TestRendering> by (
      BuilderViewFactory(
        TestRendering::class
      ) { initialRendering, initialViewEnvironment, context, _ ->
        ViewStateTestView(context).apply {
          id = initialRendering.name.hashCode()
          // For espresso matching.
          tag = initialRendering.name
          viewHooks = initialRendering.viewHooks
          initialRendering.onViewCreated(this)
          bindShowRendering(initialRendering, initialViewEnvironment) { rendering, _ ->
            rendering.onShowRendering(this)
            viewHooks = rendering.viewHooks
          }
        }
      })
  }

  private val viewEnvironment = ViewEnvironment(
    mapOf(ViewRegistry to ViewRegistry(NamedViewFactory, TestRendering))
  )
  var backstackContainer: View? = null
    private set

  var backstack: BackStackScreen<TestRendering>? = null
    set(value) {
      requireNotNull(value)
      if (value != field) {
        field = value
        backstackContainer?.showRendering(value, viewEnvironment)
      }
    }

  val currentTestView: ViewStateTestView
    get() {
      val container = backstackContainer as ViewGroup
      check(container.childCount == 1)
      return container.getChildAt(0) as ViewStateTestView
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    @Suppress("UNCHECKED_CAST")
    backstack = lastNonConfigurationInstance as BackStackScreen<TestRendering>?
      ?: BackStackScreen(TestRendering("initial"))

    check(backstackContainer == null)
    backstackContainer =
      NoTransitionBackStackContainer.buildView(backstack!!, viewEnvironment, this)
    backstackContainer!!.showRendering(backstack!!, viewEnvironment)
    setContentView(backstackContainer)
  }

  override fun onRetainNonConfigurationInstance(): Any = backstack!!
}
