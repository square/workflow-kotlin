package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.BackStackScreenViewFactory
import com.squareup.workflow1.ui.container.BodyAndModalsContainer
import com.squareup.workflow1.ui.container.BodyAndModalsScreen
import com.squareup.workflow1.ui.container.EnvironmentScreen
import com.squareup.workflow1.ui.container.EnvironmentScreenViewFactory

/**
 * [ViewEnvironment] service object used by [Screen.buildView] to find the right
 * [ScreenViewFactory]. The default implementation makes [AndroidScreen] work
 * and provides default bindings for [NamedScreen], [EnvironmentScreen], [BackStackScreen],
 * etc.
 *
 * Here is how this hook could be used to provide a custom view to handle [BackStackScreen]:
 *
 *    object MyBackStackViewFactory : ScreenViewFactory<BackStackScreen<*>>
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
 *        return MyBackStackViewFactory as ScreenViewFactory<ScreenT>
 *      return super.getViewFactoryForRenderingOrNull(environment, rendering)
 *    }
 *
 *    class MyViewModel(savedState: SavedStateHandle) : ViewModel() {
 *      val renderings: StateFlow<MyRootRendering> by lazy {
 *        val customized = ViewEnvironment.EMPTY + (ScreenViewFactoryFinder to MyFinder)
 *        renderWorkflowIn(
 *          workflow = MyRootWorkflow.mapRenderings { it.withEnvironment(customized) },
 *          scope = viewModelScope,
 *          savedStateHandle = savedState
 *        )
 *      }
 *    }
 */

@WorkflowUiExperimentalApi
public interface ScreenViewFactoryFinder {
  public fun <ScreenT : Screen> getViewFactoryForRenderingOrNull(
    environment: ViewEnvironment,
    rendering: ScreenT
  ): ScreenViewFactory<ScreenT>? {
    val entry = environment[ViewRegistry].getEntryFor(rendering::class)

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
      ?: (rendering as? NamedScreen<*>)?.let {
        NamedScreenViewFactory as ScreenViewFactory<ScreenT>
      }
      ?: (rendering as? EnvironmentScreen<*>)?.let {
        EnvironmentScreenViewFactory as ScreenViewFactory<ScreenT>
      }
  }

  public companion object : ViewEnvironmentKey<ScreenViewFactoryFinder>(
    ScreenViewFactoryFinder::class
  ) {
    override val default: ScreenViewFactoryFinder
      get() = object : ScreenViewFactoryFinder {}
  }
}
