package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ViewRegistry.Entry
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.BackStackScreenViewFactory
import com.squareup.workflow1.ui.container.BodyAndModalsContainer
import com.squareup.workflow1.ui.container.BodyAndModalsScreen
import com.squareup.workflow1.ui.container.EnvironmentScreen
import com.squareup.workflow1.ui.container.EnvironmentScreenViewFactory

/**
 * [ViewEnvironment] service object used by [Screen.buildView] to find the right
 * [ScreenViewFactory]. The default implementation makes [AndroidScreen] work
 * and provides default bindings for [NamedRendering], [EnvironmentScreen], [BackStackScreen],
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
    screen: ScreenT
  ): ScreenViewFactory<ScreenT> {
    val (resolvedScreen: Any, resolvedEnv: ViewEnvironment, factory: Entry<*>?) =
      environment.unwrapRenderingAndGetFactory(screen)

    val screenViewFactory = (factory as? ScreenViewFactory<ScreenT>)
      ?: (resolvedScreen as? AndroidScreen<*>)?.viewFactory as? ScreenViewFactory<*>
      ?: (resolvedScreen as? AsScreen<*>)?.let { AsScreenViewFactory }
      ?: (resolvedScreen as? BackStackScreen<*>)?.let {
        BackStackScreenViewFactory
      }
      ?: (resolvedScreen as? BodyAndModalsScreen<*, *>)?.let {
        BodyAndModalsContainer
      }
      ?: (resolvedScreen as? EnvironmentScreen<*>)?.let {
        EnvironmentScreenViewFactory
      }
    checkNotNull(factory) {
      val name = if (screen !== resolvedScreen) screen.toString() else "$screen($resolvedScreen)"
      "A ScreenViewFactory should have been registered to display $name, " +
        "or that class should implement AndroidScreen. Instead found $factory."
    }

    @Suppress("UNCHECKED_CAST")
    if (compatible(screen, resolvedScreen)) return screenViewFactory as ScreenViewFactory<ScreenT>

    @Suppress("UNCHECKED_CAST")
    return DecorativeScreenViewFactory(
      screen::class,
      unwrap = { wrapper, env ->
        val (unwrapped: ViewableRendering, newEnv: ViewEnvironment) = unwrapRendering(wrapper, env)
        check(compatible(unwrapped, resolvedScreen)) {
          "Expected AliasScreen $wrapper to resolve to something compatible with $resolvedScreen, " +
            "but found $unwrapped."
        }

        Pair(unwrapped as Screen, newEnv)
      }
    ) as ScreenViewFactory<ScreenT>
  }

  public companion object : ViewEnvironmentKey<ScreenViewFactoryFinder>(
    ScreenViewFactoryFinder::class
  ) {
    override val default: ScreenViewFactoryFinder
      get() = object : ScreenViewFactoryFinder {}
  }
}
