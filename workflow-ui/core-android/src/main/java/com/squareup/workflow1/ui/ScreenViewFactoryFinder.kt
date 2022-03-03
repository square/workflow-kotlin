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
import kotlin.reflect.KClass

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
    val resolved = rendering.resolve()
    val entry = environment[ViewRegistry].getEntryFor(resolved::class)

    val factory = (entry as? ScreenViewFactory<*>)
      ?: (rendering as? AndroidScreen<*>)?.viewFactory as? ScreenViewFactory<*>
      ?: (rendering as? AsScreen<*>)?.let { AsScreenViewFactory }
      ?: (rendering as? BackStackScreen<*>)?.let {
        BackStackScreenViewFactory
      }
      ?: (rendering as? BodyAndModalsScreen<*, *>)?.let {
        BodyAndModalsContainer
      }
      ?: (rendering as? EnvironmentScreen<*>)?.let {
        EnvironmentScreenViewFactory
      }

    checkNotNull(factory) {
      val name = if (rendering !== resolved) rendering.toString() else "$rendering($resolved)"
      "A ScreenViewFactory should have been registered to display $name, " +
        "or that class should implement AndroidScreen. Instead found $entry."
    }

    @Suppress("UNCHECKED_CAST")
    if (compatible(rendering, resolved)) return factory as ScreenViewFactory<ScreenT>

    @Suppress("UNCHECKED_CAST")
    return DecorativeScreenViewFactory(
      rendering::class,
      unwrap = { wrapper ->
        wrapper.resolve().also { newlyResolved ->
          check(compatible(newlyResolved, resolved)) {
            "Expected AliasScreen $wrapper to resolve to something compatible with $resolved"
          }
        }
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
