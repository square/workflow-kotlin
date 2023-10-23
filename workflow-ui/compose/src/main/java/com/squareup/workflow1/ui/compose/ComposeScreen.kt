package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Interface implemented by a rendering class to allow it to drive a composable UI via an
 * appropriate [ScreenComposableFactory] implementation, by simply overriding the [Content] method.
 * This is the compose analog to [AndroidScreen].
 *
 * **NB**: A Workflow app that relies on Compose must call [withComposeInteropSupport]
 * on its top-level [ViewEnvironment]. See that function for details.
 *
 * Note that unlike most workflow view functions, [Content] does not take the rendering as a
 * parameter. Instead, the rendering is the receiver, i.e. the current value of `this`.
 *
 * Example:
 *
 *     @OptIn(WorkflowUiExperimentalApi::class)
 *     data class HelloScreen(
 *       val message: String,
 *       val onClick: () -> Unit
 *     ) : ComposeScreen {
 *
 *       @Composable override fun Content(viewEnvironment: ViewEnvironment) {
 *         Button(onClick) {
 *           Text(message)
 *         }
 *       }
 *     }
 *
 * This is the simplest way to bridge the gap between your workflows and the UI, but using it
 * requires your workflows code to reside in Android modules and depend upon the Compose runtime,
 * instead of being pure Kotlin. If this is a problem, or you need more flexibility for any other
 * reason, you can use [ViewRegistry] to bind your renderings to [ScreenComposableFactory]
 * implementations at runtime.
 *
 * ## Nesting child renderings
 *
 * Workflows can render other workflows, and renderings from one workflow can contain renderings
 * from other workflows. These renderings may all be bound to their own UI factories.
 * A classic [ScreenViewFactory][com.squareup.workflow1.ui.ScreenViewFactory] can
 * use [WorkflowViewStub][com.squareup.workflow1.ui.WorkflowViewStub] to recursively show nested
 * renderings.
 *
 * Compose-based UI may also show nested renderings. Doing so is as simple
 * as calling [WorkflowRendering] and passing in the nested rendering.
 * See the kdoc on that function for an example.
 *
 * Nested renderings will have access to any
 * [composition locals][androidx.compose.runtime.CompositionLocal] defined in outer composable, even
 * if there are legacy views in between them, as long as the [ViewEnvironment] is propagated
 * continuously between the two factories.
 *
 * ## Initializing Compose context (Theming)
 *
 * Often all the [ScreenComposableFactory] factories in an app need to share some context –
 * for example, certain composition locals need to be provided, such as `MaterialTheme`.
 * To configure this shared context, call [withCompositionRoot] on your top-level [ViewEnvironment].
 * The first time a [ScreenComposableFactory] is used to show a rendering, its [Content] function
 * will be wrapped with the [CompositionRoot]. See the documentation on [CompositionRoot] for
 * more information.
 */
@WorkflowUiExperimentalApi
public interface ComposeScreen : Screen {

  /**
   * The composable content of this rendering. This method will be called with the current rendering
   * instance as the receiver, any time a new rendering is emitted, or the [viewEnvironment]
   * changes.
   */
  @Composable public fun Content(viewEnvironment: ViewEnvironment)
}

/**
 * Convenience function for creating anonymous [ComposeScreen]s since composable fun interfaces
 * aren't supported. See the [ComposeScreen] class for more information.
 */
@WorkflowUiExperimentalApi
public inline fun ComposeScreen(
  crossinline content: @Composable (ViewEnvironment) -> Unit
): ComposeScreen = object : ComposeScreen {
  @Composable override fun Content(viewEnvironment: ViewEnvironment) {
    content(viewEnvironment)
  }
}
