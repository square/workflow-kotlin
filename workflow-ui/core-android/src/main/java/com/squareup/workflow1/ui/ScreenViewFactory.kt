package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

@WorkflowUiExperimentalApi
public interface ScreenViewFactory<in ScreenT : Screen> : ViewRegistry.Entry<ScreenT> {
  public fun buildView(
    contextForNewView: Context,
    container: ViewGroup? = null
  ): View

  public fun updateView(
    view: View,
    rendering: ScreenT,
    viewEnvironment: ViewEnvironment,
  )
}

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.toViewFactory(
  viewEnvironment: ViewEnvironment
): ScreenViewFactory<ScreenT> {
  return viewEnvironment[ScreenViewFactoryFinder].getViewFactoryForRendering(
    viewEnvironment, this
  )
}

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewFactory<ScreenT>.start(
  initialRendering: ScreenT,
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  // TODO can this be made less general, just findParentLifecycle?
  viewStarter: ViewStarter = ViewStarter { _, doStart ->
    doStart()
  }
): ScreenViewHolder<ScreenT> {
  return ScreenViewHolder(
    this,
    initialRendering,
    buildView(contextForNewView, container)
  ).also {
    viewStarter.startView(it.view) {
      updateView(it.view, initialRendering, initialViewEnvironment)
    }
  }
}

/**
 * A wrapper for the function invoked when [View.start] is called, allowing for
 * last second initialization of a newly built [View]. Provided via [ScreenViewFactory.start].
 */
@WorkflowUiExperimentalApi
public fun interface ViewStarter {
  /** Called from [View.start]. [doStart] must be invoked. */
  public fun startView(
    view: View,
    doStart: () -> Unit
  )
}
