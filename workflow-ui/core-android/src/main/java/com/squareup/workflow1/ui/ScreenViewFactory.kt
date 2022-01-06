package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.BackStackScreenViewFactory
import com.squareup.workflow1.ui.container.BodyAndModalsContainer
import com.squareup.workflow1.ui.container.BodyAndModalsScreen
import com.squareup.workflow1.ui.container.EnvironmentScreen
import com.squareup.workflow1.ui.container.EnvironmentScreenViewFactory

/**
 * Factory for [View] instances that can show renderings of type [ScreenT] : [Screen].
 *
 * Two concrete [ScreenViewFactory] implementations are provided:
 *
 *  - The various [bind][ScreenViewUpdater.bind] methods on [ScreenViewUpdater] allow easy use of
 *    Android XML layout resources and [AndroidX ViewBinding][androidx.viewbinding.ViewBinding].
 *
 *  - [ManualScreenViewFactory] allows views to be built from code.
 *
 * It's simplest to have your rendering classes implement [AndroidScreen] to associate
 * them with appropriate an appropriate [ScreenViewFactory]. For more flexibility, and to
 * avoid coupling your workflow directly to the Android runtime, see [ViewRegistry].
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactory<ScreenT : Screen> : ViewRegistry.Entry<ScreenT> {
  /**
   * Returns a View ready to display [initialRendering] (and any succeeding values)
   * via [View.showRendering].
   */
  public fun buildView(
    initialRendering: ScreenT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup? = null
  ): ScreenView<ScreenT>
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
 * @throws IllegalArgumentException if no builder can be found for type [ScreenT]
 *
 * @throws IllegalStateException if the matching [ScreenViewFactory] fails to call
 * [View.bindShowRendering] when constructing the view
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.buildView(
  viewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null
): ScreenView<ScreenT> {
  return viewEnvironment.getViewFactoryForRendering(this).buildView(
    this, viewEnvironment, contextForNewView, container
  )
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
@Deprecated("Use WorkflowView.Starter")
public fun interface ViewStarter {
  /** Called from [View.start]. [doStart] must be invoked. */
  public fun startView(
    view: View,
    doStart: () -> Unit
  )
}

@WorkflowUiExperimentalApi
internal fun <ScreenT : Screen>
  ViewEnvironment.getViewFactoryForRendering(rendering: ScreenT): ScreenViewFactory<ScreenT> {
  val entry = get(ViewRegistry).getEntryFor(rendering::class)

  @Suppress("UNCHECKED_CAST", "DEPRECATION")
  return (entry as? ScreenViewFactory<ScreenT>)
    ?: (rendering as? AndroidScreen<*>)?.viewFactory as? ScreenViewFactory<ScreenT>
    ?: (rendering as? AsScreen<*>)?.let { AsScreenViewFactory as ScreenViewFactory<ScreenT> }
    ?: (rendering as? BackStackScreen<*>)?.let {
      BackStackScreenViewFactory as ScreenViewFactory<ScreenT>
    }
    ?: (rendering as? BodyAndModalsScreen<*, *>)?.let {
      BodyAndModalsContainer as ScreenViewFactory<ScreenT>
    }
    ?: (rendering as? NamedScreen<*>)?.let { NamedScreenViewFactory as ScreenViewFactory<ScreenT> }
    ?: (rendering as? EnvironmentScreen<*>)?.let {
      EnvironmentScreenViewFactory as ScreenViewFactory<ScreenT>
    }
    ?: throw IllegalArgumentException(
      "A ScreenViewFactory should have been registered to display $rendering, " +
        "or that class should implement AndroidScreen. Instead found $entry."
    )
}
