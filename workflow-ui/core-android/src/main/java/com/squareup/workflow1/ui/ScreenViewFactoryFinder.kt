package com.squareup.workflow1.ui

// import com.squareup.workflow1.ui.container.BackStackScreenViewFactory
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.EnvironmentScreen
import kotlin.reflect.KFunction3

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
    // TODO: reverse these, everywhere else rendering comes first
    environment: ViewEnvironment,
    rendering: ScreenT
  ): ScreenViewFactory<ScreenT> {
    val entry = environment[ViewRegistry].getEntryFor(rendering::class)

    @Suppress("UNCHECKED_CAST")
    return (entry as? ScreenViewFactory<ScreenT>)
      ?: (rendering as? AndroidScreen<*>)?.viewFactory as? ScreenViewFactory<ScreenT>
      ?: (rendering as? AsScreen<*>)?.let { asScreen ->
        object : ScreenViewFactory<AsScreen<*>> {
          override val type = AsScreen::class

          @Suppress("DEPRECATION")
          override fun buildView(
            contextForNewView: Context,
            container: ViewGroup?
          ): View = environment[ViewRegistry].buildView(
            asScreen.rendering,
            environment,
            contextForNewView,
            container
          )

          override fun updateView(
            view: View,
            rendering: AsScreen<*>,
            viewEnvironment: ViewEnvironment
          ) {
            view.showRendering(rendering.rendering, viewEnvironment)
          }
        } as ScreenViewFactory<ScreenT>
      }
      // ?: (rendering as? BackStackScreen<*>)?.let {
      //   BackStackScreenViewFactory as ScreenViewFactory<ScreenT>
      // }
      // ?: (rendering as? BodyAndModalsScreen<*, *>)?.let {
      //   BodyAndModalsContainer as ScreenViewFactory<ScreenT>
      // }
      // ?: (rendering as? NamedScreen<*>)?.let {
      //   NamedScreenViewFactory as ScreenViewFactory<ScreenT>
      // }
      ?: (rendering as? EnvironmentScreen<*>)?.let { environmentScreen ->
        val realFactory = (environment merge environmentScreen.viewEnvironment).let { env ->
          env[ScreenViewFactoryFinder].getViewFactoryForRendering(
            env, environmentScreen.screen
          )
        }

        object : ScreenViewFactory<EnvironmentScreen<*>> {
          override val type = EnvironmentScreen::class

          override fun buildView(
            contextForNewView: Context,
            container: ViewGroup?
          ) = realFactory.buildView(contextForNewView, container)

          override fun updateView(
            view: View,
            rendering: EnvironmentScreen<*>,
            viewEnvironment: ViewEnvironment
          ) {
            realFactory.updateView(
              view, rendering.screen, viewEnvironment merge rendering.viewEnvironment
            )
          }
        } as ScreenViewFactory<ScreenT>
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
