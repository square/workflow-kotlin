package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ScreenViewFactory.Companion.forWrapper
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.BackStackScreenViewFactory
import com.squareup.workflow1.ui.container.BodyAndOverlaysContainer
import com.squareup.workflow1.ui.container.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.container.EnvironmentScreen
import com.squareup.workflow1.ui.container.EnvironmentScreenViewFactory

/**
 * [ViewEnvironment] service object used by [Screen.toViewFactory] to find the right
 * [ScreenViewFactory] to build and manage a [View][android.view.View] to display
 * [Screen]s of the type of the receiver. The default implementation makes [AndroidScreen]
 * work, and provides default bindings for [NamedScreen], [EnvironmentScreen], [BackStackScreen],
 * etc.
 *
 * Here is how this hook could be used to provide a custom view to handle [BackStackScreen]:
 *
 *    object MyViewFactory : ScreenViewFactory<BackStackScreen<*>>
 *    by ScreenViewFactory(
 *      buildView = { environment, context, _ ->
 *        val view = MyBackStackContainer(context)
 *          .apply { layoutParams = (LayoutParams(MATCH_PARENT, MATCH_PARENT)) }
 *        ScreenViewHolder(environment, view) { rendering, environment ->
 *          view.update(rendering, environment)
 *        }
 *      }
 *    )
 *
 *    object MyFinder : ScreenViewFactoryFinder {
 *      override fun <ScreenT : Screen> getViewFactoryForRendering(
 *        environment: ViewEnvironment,
 *        rendering: ScreenT
 *      ): ScreenViewFactory<ScreenT> {
 *        @Suppress("UNCHECKED_CAST")
 *        if (rendering is BackStackScreen<*>) return MyViewFactory as ScreenViewFactory<ScreenT>
 *        return super.getViewFactoryForRendering(environment, rendering)
 *      }
 *    }
 *
 *    class MyViewModel(savedState: SavedStateHandle) : ViewModel() {
 *      val renderings: StateFlow<MyRootRendering> by lazy {
 *        val env = ViewEnvironment.EMPTY + (ScreenViewFactoryFinder to MyFinder)
 *        renderWorkflowIn(
 *          workflow = MyRootWorkflow.mapRenderings { it.withEnvironment(env) },
 *          scope = viewModelScope,
 *          savedStateHandle = savedState
 *        )
 *      }
 *    }
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactoryFinder {
  public fun <ScreenT : Screen> getViewFactoryForRendering(
    environment: ViewEnvironment,
    rendering: ScreenT
  ): ScreenViewFactory<ScreenT> {
    val entry = environment[ViewRegistry].getEntryFor(rendering::class)

    @Suppress("UNCHECKED_CAST")
    return (entry as? ScreenViewFactory<ScreenT>)
      ?: (rendering as? AndroidScreen<*>)?.viewFactory as? ScreenViewFactory<ScreenT>
      ?: (rendering as? AsScreen<*>)?.let {
        AsScreenViewFactory(it, environment) as ScreenViewFactory<ScreenT>
      }
      ?: (rendering as? BackStackScreen<*>)?.let {
        BackStackScreenViewFactory as ScreenViewFactory<ScreenT>
      }
      ?: (rendering as? BodyAndOverlaysScreen<*, *>)?.let {
        BodyAndOverlaysContainer as ScreenViewFactory<ScreenT>
      }
      ?: (rendering as? NamedScreen<*>)?.let {
        forWrapper<NamedScreen<ScreenT>, ScreenT> { it.wrapped } as ScreenViewFactory<ScreenT>
      }
      ?: (rendering as? EnvironmentScreen<*>)?.let {
        EnvironmentScreenViewFactory<ScreenT>() as ScreenViewFactory<ScreenT>
      }
      ?: throw IllegalArgumentException(
        "A ScreenViewFactory should have been registered to display $rendering, " +
          "or that class should implement AndroidScreen. Instead found $entry."
      )
  }

  public companion object : ViewEnvironmentKey<ScreenViewFactoryFinder>(
    ScreenViewFactoryFinder::class
  ) {
    override val default: ScreenViewFactoryFinder
      get() = object : ScreenViewFactoryFinder {}
  }
}
