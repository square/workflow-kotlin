package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Factory for [View] instances that can show renderings of type [RenderingT] : [Screen].
 *
 * Two concrete [ScreenViewFactory] implementations are provided:
 *
 *  - The various [bind][ScreenViewRunner.bind] methods on [ScreenViewRunner] allow easy use of
 *    Android XML layout resources and [AndroidX ViewBinding][androidx.viewbinding.ViewBinding].
 *
 *  - [ManualScreenViewFactory] allows views to be built from code.
 *
 * It's simplest to have your rendering classes implement [AndroidScreen] to associate
 * them with appropriate an appropriate [ScreenViewFactory]. For more flexibility, and to
 * avoid coupling your workflow directly to the Android runtime, see [ViewRegistry].
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactory<in RenderingT : Screen> : ViewRegistry.Entry<RenderingT> {
  /**
   * Returns a View ready to display [initialRendering] (and any succeeding values)
   * via [View.showRendering].
   */
  public fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup? = null
  ): View
}

/**
 * It is usually more convenient to use [WorkflowViewStub] or [DecorativeScreenViewFactory]
 * than to call this method directly.
 *
 * Finds a [ScreenViewFactory] to create a [View] to display the receiving [Screen].
 * The caller is responsible for calling [View.start] on the new [View]. After that,
 * [View.showRendering] can be used to update it with new renderings that
 * are [compatible] with this [Screen]. [WorkflowViewStub] takes care of this chore itself.
 *
 * @param viewStarter An optional wrapper for the function invoked when [View.start]
 * is called, allowing for last second initialization of a newly built [View].
 * See [ViewStarter] for details.
 *
 * @throws IllegalArgumentException if no builder can be found for type [ScreenT]
 *
 * @throws IllegalStateException if the matching [ScreenViewFactory] fails to call
 * [View.bindShowRendering] when constructing the view
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.buildView(
  viewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  viewStarter: ViewStarter? = null,
): View {
  val viewFactory = viewEnvironment[ScreenViewFactoryFinder].getViewFactoryForRendering(
    viewEnvironment, this
  )

  return viewFactory.buildView(this, viewEnvironment, contextForNewView, container).also { view ->
    checkNotNull(view.workflowViewStateOrNull) {
      "View.bindShowRendering should have been called for $view, typically by the " +
        "ScreenViewFactory that created it."
    }
    viewStarter?.let { givenStarter ->
      val doStart = view.starter
      view.starter = { newView ->
        givenStarter.startView(newView) { doStart.invoke(newView) }
      }
    }
  }
}

/**
 * A wrapper for the function invoked when [View.start] is called, allowing for
 * last second initialization of a newly built [View]. Provided via [Screen.buildView]
 * or [DecorativeScreenViewFactory.viewStarter].
 *
 * While [View.getRendering] may be called from [startView], it is not safe to
 * assume that the type of the rendering retrieved matches the type the view was
 * originally built to display. [ScreenViewFactory] instances can be wrapped, and
 * renderings can be mapped to other types.
 */
@WorkflowUiExperimentalApi
public fun interface ViewStarter {
  /** Called from [View.start]. [doStart] must be invoked. */
  public fun startView(
    view: View,
    doStart: () -> Unit
  )
}
