package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.ScreenViewFactory.Companion.forWrapper
import com.squareup.workflow1.ui.ViewRegistry.Key
import com.squareup.workflow1.ui.navigation.BackStackScreen
import com.squareup.workflow1.ui.navigation.BackStackScreenViewFactory
import com.squareup.workflow1.ui.navigation.BodyAndOverlaysContainer
import com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen

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
public interface ScreenViewFactoryFinder {
  public fun <ScreenT : Screen> getViewFactoryForRendering(
    environment: ViewEnvironment,
    rendering: ScreenT
  ): ScreenViewFactory<ScreenT>? {
    val factoryOrNull: ScreenViewFactory<ScreenT>? =
      environment[ViewRegistry].getFactoryFor(rendering)

    @Suppress("UNCHECKED_CAST")
    return factoryOrNull
      ?: (rendering as? AndroidScreen<*>)?.viewFactory as? ScreenViewFactory<ScreenT>
      ?: (rendering as? BackStackScreen<*>)?.let {
        BackStackScreenViewFactory as ScreenViewFactory<ScreenT>
      }
      ?: (rendering as? BodyAndOverlaysScreen<*, *>)?.let {
        BodyAndOverlaysContainer as ScreenViewFactory<ScreenT>
      }
      ?: (rendering as? NamedScreen<*>)?.let {
        forWrapper<NamedScreen<ScreenT>, ScreenT>() as ScreenViewFactory<ScreenT>
      }
      ?: (rendering as? EnvironmentScreen<*>)?.let {
        forWrapper<EnvironmentScreen<ScreenT>, ScreenT>(
          prepEnvironment = { e -> e + rendering.environment }
        ) { _, envScreen, environment, showContent ->
          showContent(envScreen.content, environment + envScreen.environment)
        } as ScreenViewFactory<ScreenT>
      }
  }

  public companion object : ViewEnvironmentKey<ScreenViewFactoryFinder>() {
    override val default: ScreenViewFactoryFinder
      get() = object : ScreenViewFactoryFinder {}
  }
}

public fun <ScreenT : Screen> ScreenViewFactoryFinder.requireViewFactoryForRendering(
  environment: ViewEnvironment,
  rendering: ScreenT
): ScreenViewFactory<ScreenT> {
  return getViewFactoryForRendering(environment, rendering)
    ?: throw IllegalArgumentException(
      "A ScreenViewFactory should have been registered to display $rendering, " +
        "or that class should implement AndroidScreen. Instead found " +
        "${
          environment[ViewRegistry]
            .getEntryFor(Key(rendering::class, ScreenViewFactory::class))
        }. If this rendering is Compose based, you may be missing a call to " +
        "ViewEnvironment.withComposeInteropSupport() " +
        "from module com.squareup.workflow1:workflow-ui-compose at the top " +
        "of your Android view hierarchy."
    )
}
