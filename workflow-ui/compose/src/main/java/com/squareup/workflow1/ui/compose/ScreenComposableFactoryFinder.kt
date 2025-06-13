package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.squareup.workflow1.internal.withKey
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.EnvironmentScreen
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.ViewRegistry.Key
import com.squareup.workflow1.ui.getFactoryFor

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
        ScreenComposableFactory<ComposeScreen> { rendering ->
          rendering.Content()
        } as ScreenComposableFactory<ScreenT>
      }

      // Support for Compose BackStackScreen, BodyAndOverlaysScreen treatments would go here,
      // if it were planned. See similar blocks in ScreenViewFactoryFinder

      ?: (rendering as? NamedScreen<*>)?.let {
        ScreenComposableFactory<NamedScreen<*>> { rendering ->
          val innerFactory = rendering.content
            .toComposableFactory(LocalWorkflowEnvironment.current)
          innerFactory.Content(rendering.content)
        } as ScreenComposableFactory<ScreenT>
      }
      ?: (rendering as? EnvironmentScreen<*>)?.let {
        ScreenComposableFactory<EnvironmentScreen<*>> { rendering ->
          val currentEnv = LocalWorkflowEnvironment.current
          val innerFactory = rendering.content.toComposableFactory(
            currentEnv + rendering.environment
          )

          val comboEnv = remember(currentEnv, rendering.environment) {
            currentEnv + rendering.environment
          }
          CompositionLocalProvider(LocalWorkflowEnvironment provides comboEnv) {
            innerFactory.Content(rendering.content)
          }
        } as ScreenComposableFactory<ScreenT>
      }
  }

  public companion object : ViewEnvironmentKey<ScreenComposableFactoryFinder>() {
    override val default: ScreenComposableFactoryFinder
      get() = object : ScreenComposableFactoryFinder {}
  }
}

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
    ).withKey(keyFor(rendering))
}
