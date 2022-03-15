package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.BackStackScreenViewFactory
import com.squareup.workflow1.ui.container.BodyAndModalsContainer
import com.squareup.workflow1.ui.container.BodyAndModalsScreen
import com.squareup.workflow1.ui.container.EnvironmentScreen
import com.squareup.workflow1.ui.container.EnvironmentScreenViewFactory

// TODO: stale
/**
 * [ViewEnvironment] service object used by [Screen.buildView] to find the right
 * [ScreenViewFactory]. The default implementation makes [AndroidScreen] work
 * and provides default bindings for [NamedScreen], [EnvironmentScreen], [BackStackScreen],
 * etc.
 *
 * Here is how this hook could be used to provide a custom view to handle [BackStackScreen]:
 *
 *    object MyViewFactory : ScreenViewFactory<BackStackScreen<*>>
 *    by ManualScreenViewFactory(
 *      type = BackStackScreen::class,
 *      viewConstructor = { initialRendering, initialEnv, context, _ ->
 *        MyBackStackContainer(context)
 *          .apply {
 *            layoutParams = (LayoutParams(MATCH_PARENT, MATCH_PARENT))
 *            bindShowRendering(initialRendering, initialEnv, ::update)
 *          }
 *      }
 *    )
 *
 *    object MyFinder : ScreenViewFactoryFinder {
 *      @Suppress("UNCHECKED_CAST")
 *      if (rendering is BackStackScreen<*>)
 *        return MyViewFactory as ScreenViewFactory<ScreenT>
 *      return super.getViewFactoryForRendering(environment, rendering)
 *    }
 *
 *    class MyViewModel(savedState: SavedStateHandle) : ViewModel() {
 *      val renderings: StateFlow<MyRootRendering> by lazy {
 *        val customized = ViewEnvironment.EMPTY + (ScreenViewFactoryFinder to MyFinder)
 *        renderWorkflowIn(
 *          workflow = MyRootWorkflow.withEnvironment(customized),
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
      ?: (rendering as? BodyAndModalsScreen<*, *>)?.let {
        BodyAndModalsContainer as ScreenViewFactory<ScreenT>
      }
      ?: (rendering as? NamedScreen<*>)?.let {
        NamedScreenViewFactory(it, environment) as ScreenViewFactory<ScreenT>
      }
      ?: (rendering as? EnvironmentScreen<*>)?.let { environmentScreen ->
        EnvironmentScreenViewFactory(environmentScreen, environment) as ScreenViewFactory<ScreenT>
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
