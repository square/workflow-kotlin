package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Interface implemented by a rendering class to allow it to drive an Android UI
 * via an appropriate [ScreenViewFactory] implementation.
 *
 * You will rarely, if ever, write a [ScreenViewFactory] yourself.  Instead
 * use [ScreenViewRunner.bind] to work with XML layout resources, or
 * [BuilderViewFactory] to create views from code.  See [ScreenViewRunner] for more
 * details.
 *
 *     @OptIn(WorkflowUiExperimentalApi::class)
 *     data class HelloScreen(
 *       val message: String,
 *       val onClick: () -> Unit
 *     ) : AndroidScreen<HelloScreen> {
 *       override val viewFactory =
 *         ScreenViewRunner.bind(HelloGoodbyeLayoutBinding::inflate) { screen, _ ->
 *           helloMessage.text = screen.message
 *           helloMessage.setOnClickListener { screen.onClick() }
 *         }
 *     }
 *
 * This is the simplest way to bridge the gap between your workflows and the UI,
 * but using it requires your workflows code to reside in Android modules, instead
 * of pure Kotlin. If this is a problem, or you need more flexibility for any other
 * reason, you can use [ViewRegistry] to bind your renderings to [ScreenViewFactory]
 * implementations at runtime. Also note that a [ViewRegistry] entry will override
 * the [viewFactory] returned by an [AndroidScreen].
 */
@WorkflowUiExperimentalApi
public interface AndroidScreen<V : AndroidScreen<V>> : Screen {
  /**
   * Used to build instances of [android.view.View] as needed to
   * display renderings of this type.
   */
  public val viewFactory: ScreenViewFactory<V>
}

/**
 * It is usually more convenient to use [WorkflowViewStub] or [DecorativeScreenViewFactory]
 * than to call this method directly.
 *
 * Finds a [ScreenViewFactory] to create a [View] to display [this@buildView]. The new view
 * can be updated via calls to [View.showRendering] -- that is, it is guaranteed that
 * [bindShowRendering] has been called on this view.
 *
 * The returned view will have a
 * [WorkflowLifecycleOwner][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner]
 * set on it. The returned view must EITHER:
 *
 * 1. Be attached at least once to ensure that the lifecycle eventually gets destroyed (because its
 *    parent is destroyed), or
 * 2. Have its
 *    [WorkflowLifecycleOwner.destroyOnDetach][com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner.destroyOnDetach]
 *    called, which will either schedule the
 *    lifecycle to be destroyed if the view is attached, or destroy it immediately if it's detached.
 *
 * [WorkflowViewStub] takes care of this chore itself.
 *
 * @param initializeView Optional function invoked immediately after the [View] is
 * created (that is, immediately after the call to [ScreenViewFactory.buildView]).
 * [showRendering], [getRendering] and [environment] are all available when this is called.
 * Defaults to a call to [View.showFirstRendering].
 *
 * @throws IllegalArgumentException if no builder can be find for type [ScreenT]
 *
 * @throws IllegalStateException if the matching [ScreenViewFactory] fails to call
 * [View.bindShowRendering] when constructing the view
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.buildView(
  viewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  initializeView: View.() -> Unit = { showFirstRendering() }
): View {
  val entry = viewEnvironment.getViewFactoryForRendering(this)
  val viewFactory = (entry as? ScreenViewFactory<ScreenT>)
    ?: error("Require a ScreenViewFactory for $this, found $entry")

  return viewFactory.buildView(
    this, viewEnvironment, contextForNewView, container
  ).also { view ->
    checkNotNull(view.showRenderingTag) {
      "View.bindShowRendering should have been called for $view, typically by the " +
        "${ScreenViewFactory::class.java.name} that created it."
    }
    initializeView.invoke(view)
  }
}
