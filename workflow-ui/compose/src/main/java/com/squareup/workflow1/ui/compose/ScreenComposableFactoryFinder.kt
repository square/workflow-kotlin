package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.squareup.workflow1.ui.EnvironmentScreen
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.ViewRegistry.Key
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.getFactoryFor
import com.squareup.workflow1.ui.navigation.BackButtonScreen

@WorkflowUiExperimentalApi
public interface ScreenComposableFactoryFinder {
  public fun <ScreenT : Screen> getComposableFactoryForRendering(
    environment: ViewEnvironment,
    rendering: ScreenT
  ): ScreenComposableFactory<ScreenT>? {
    val factoryOrNull: ScreenComposableFactory<ScreenT>? =
      environment[ViewRegistry].getFactoryFor(rendering)

    @Suppress("UNCHECKED_CAST")
    return factoryOrNull
      ?: (rendering as? ComposeScreen)?.let {
        ScreenComposableFactory<ComposeScreen> { composeScreen ->
          composeScreen.Content()
        } as ScreenComposableFactory<ScreenT>
      }

      // Support for Compose BackStackScreen, BodyAndOverlaysScreen treatments would go here,
      // if it were planned. See similar blocks in ScreenViewFactoryFinder
      ?: (rendering as? BackButtonScreen<*>)?.let {
        ScreenComposableFactory<BackButtonScreen<*>> { backButtonScreen ->
          // now do the back button stuff
          WorkflowRendering(backButtonScreen.content)
        } as ScreenComposableFactory<ScreenT>
      }

      ?: (rendering as? NamedScreen<*>)?.let {
        ScreenComposableFactory<NamedScreen<*>> { namedScreen ->
          val innerFactory = namedScreen.content
            .toComposableFactory(LocalWorkflowEnvironment.current)
          innerFactory.Content(namedScreen.content)
        } as ScreenComposableFactory<ScreenT>
      }
      ?: (rendering as? EnvironmentScreen<*>)?.let {
        ScreenComposableFactory<EnvironmentScreen<*>> { envScreen ->
          val currentEnv = LocalWorkflowEnvironment.current
          val innerFactory = envScreen.content.toComposableFactory(
            currentEnv + envScreen.environment
          )

          val comboEnv = remember(currentEnv, envScreen.environment) {
            currentEnv + envScreen.environment
          }
          CompositionLocalProvider(LocalWorkflowEnvironment provides comboEnv) {
            innerFactory.Content(envScreen.content)
          }
        } as ScreenComposableFactory<ScreenT>
      }
  }

  public companion object : ViewEnvironmentKey<ScreenComposableFactoryFinder>() {
    override val default: ScreenComposableFactoryFinder
      get() = object : ScreenComposableFactoryFinder {}
  }
}

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenComposableFactoryFinder.requireComposableFactoryForRendering(
  environment: ViewEnvironment,
  rendering: ScreenT
): ScreenComposableFactory<ScreenT> {
  return getComposableFactoryForRendering(environment, rendering)
    ?: throw IllegalArgumentException(
      "A ScreenComposableFactory should have been registered to display $rendering, " +
        "or that class should implement ComposeScreen. Instead found " +
        "${
          environment[ViewRegistry]
            .getEntryFor(Key(rendering::class, ScreenComposableFactory::class))
        }."
    )
}
