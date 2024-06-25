package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.ViewRegistry.Key
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
public inline fun <reified ScreenT : Screen> ScreenComposableFactory(
  noinline content: @Composable (
    rendering: ScreenT,
    environment: ViewEnvironment
  ) -> Unit
): ScreenComposableFactory<ScreenT> = ScreenComposableFactory(ScreenT::class, content)

@PublishedApi
@WorkflowUiExperimentalApi
internal fun <ScreenT : Screen> ScreenComposableFactory(
  type: KClass<in ScreenT>,
  content: @Composable (
    rendering: ScreenT,
    environment: ViewEnvironment
  ) -> Unit
): ScreenComposableFactory<ScreenT> = object : ScreenComposableFactory<ScreenT> {
  override val type: KClass<in ScreenT> = type

  @Composable override fun Content(
    rendering: ScreenT,
    environment: ViewEnvironment
  ) {
    content(rendering, environment)
  }
}

/**
 * A [ViewRegistry.Entry] that uses a [Composable] function to display [ScreenT].
 * This is the fundamental unit of Compose tooling in Workflow UI, the Compose analogue of
 * [ScreenViewFactory][com.squareup.workflow1.ui.ScreenViewFactory].
 *
 * [ScreenComposableFactory] is also a bit cumbersome to use directly,
 * so [ComposeScreen] is provided as a convenience. Most developers will
 * have no reason to work with [ScreenComposableFactory] directly, or even
 * be aware of it.
 *
 * - See [ComposeScreen] for a more complete description of using Compose to
 *   build a Workflow-based UI.
 *
 * - See [WorkflowRendering] to display a nested [Screen] from [ComposeScreen.Content]
 *   or from [ScreenComposableFactory.Content]
 *
 * Use [ScreenComposableFactory] directly if you need to prevent your
 * [Screen] rendering classes from depending on Compose at compile time.
 *
 * Example:
 *
 *     val fooComposableFactory = ScreenComposableFactory<FooScreen> { screen, _ ->
 *       Text(screen.message)
 *     }
 *
 *     val viewRegistry = ViewRegistry(fooComposableFactory, …)
 *     val viewEnvironment = ViewEnvironment.EMPTY + viewRegistry
 *
 *     renderWorkflowIn(
 *       workflow = MyWorkflow.mapRendering { it.withEnvironment(viewEnvironment) }
 *     )
 */
@WorkflowUiExperimentalApi
public interface ScreenComposableFactory<in ScreenT : Screen> : ViewRegistry.Entry<ScreenT> {
  public val type: KClass<in ScreenT>

  override val key: Key<ScreenT, ScreenComposableFactory<*>>
    get() = Key(type, ScreenComposableFactory::class)

  /**
   * The composable content of this [ScreenComposableFactory]. This method will be called
   * any time [rendering] or [environment] change. It is the Compose-based analogue of
   * [ScreenViewRunner.showRendering][com.squareup.workflow1.ui.ScreenViewRunner.showRendering].
   */
  @Composable public fun Content(
    rendering: ScreenT,
    environment: ViewEnvironment
  )
}

/**
 * It is rare to call this method directly. Instead the most common path is to pass [Screen]
 * instances to [WorkflowRendering], which will apply the [ScreenComposableFactory]
 * and [ScreenComposableFactoryFinder] machinery for you.
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.toComposableFactory(
  environment: ViewEnvironment
): ScreenComposableFactory<ScreenT> {
  return environment[ScreenComposableFactoryFinder]
    .requireComposableFactoryForRendering(environment, this)
}
