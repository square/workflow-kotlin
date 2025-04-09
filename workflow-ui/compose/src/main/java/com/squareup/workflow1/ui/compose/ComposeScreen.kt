package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry

/**
 * Interface implemented by a rendering class to allow it to drive a composable UI via an
 * appropriate [ScreenComposableFactory] implementation, by simply overriding the [Content] method.
 *
 * Note that it is generally an error for a [Workflow][com.squareup.workflow1.Workflow]
 * to declare [ComposeScreen] as its `RenderingT` type -- prefer [Screen] for that.
 * [ComposeScreen], like [AndroidScreen][com.squareup.workflow1.ui.AndroidScreen],
 * is strictly a possible implementation detail of [Screen]. It is a convenience to
 * minimize the boilerplate required to set up a [ScreenComposableFactory].
 * (That interface is the fundamental unit of Compose tooling for Workflow UI.
 * But in day to day use, most developer will work with [ComposeScreen] and be only
 * vaguely aware of the existence of [ScreenComposableFactory],
 * so the bulk of our description of working with Compose is here.)
 *
 * **NB**: A Workflow app that relies on Compose must call [withComposeInteropSupport]
 * on its top-level [ViewEnvironment]. See that function for details.
 *
 * Note that unlike most workflow view functions, [Content] does not take the rendering as a
 * parameter. Instead, the rendering is the receiver, i.e. the current value of `this`.
 *
 * Example:
 *
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
public interface ComposeScreen : Screen {

  /**
   * The composable content of this rendering. This method will be called with the
   * current rendering instance as the receiver any time a new rendering is emitted.
   */
  @Composable public fun Content()
}

/**
 * Convenience function for creating anonymous [ComposeScreen]s since composable fun interfaces
 * aren't supported. See the [ComposeScreen] class for more information.
 */
public inline fun ComposeScreen(
  crossinline content: @Composable () -> Unit
): ComposeScreen = object : ComposeScreen {
  @Composable override fun Content() {
    content()
  }
}
