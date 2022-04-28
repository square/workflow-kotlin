package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

/**
 * Interface implemented by a rendering class to allow it to drive a composable UI via an
 * appropriate [ComposeScreenViewFactory] implementation, by simply overriding the [Content] method.
 * This is the compose analog to [AndroidScreen].
 *
 * Note that unlike most workflow view functions, [Content] does not take the rendering as a
 * parameter. Instead, the rendering is the receiver, i.e. the current value of `this`.
 *
 * Example:
 *
 * ```
 * @OptIn(WorkflowUiExperimentalApi::class)
 * data class HelloView(
 *   val message: String,
 *   val onClick: () -> Unit
 * ) : ComposeScreen {
 *
 *   @Composable override fun Content(viewEnvironment: ViewEnvironment) {
 *     Button(onClick) {
 *       Text(message)
 *     }
 *   }
 * }
 * ```
 *
 * This is the simplest way to bridge the gap between your workflows and the UI, but using it
 * requires your workflows code to reside in Android modules, instead of pure Kotlin. If this is a
 * problem, or you need more flexibility for any other reason, you can use [ViewRegistry] to bind
 * your renderings to [ComposeScreenViewFactory] implementations at runtime.
 */
@WorkflowUiExperimentalApi
public interface ComposeScreen : AndroidScreen<Nothing> {

  /** Don't override this, override [Content] instead. */
  override val viewFactory: ScreenViewFactory<Nothing> get() = Companion

  /**
   * The composable content of this rendering. This method will be called with the current rendering
   * instance as the receiver, any time a new rendering is emitted, or the [viewEnvironment]
   * changes.
   */
  @Composable public fun Content(viewEnvironment: ViewEnvironment)

  private companion object : ComposeScreenViewFactory<ComposeScreen>() {
    /**
     * Just returns [ComposeScreen]'s class, since this factory isn't for using with a view
     * registry it doesn't matter.
     */
    override val type: KClass<in ComposeScreen> = ComposeScreen::class

    @Composable override fun Content(
      rendering: ComposeScreen,
      viewEnvironment: ViewEnvironment
    ) {
      rendering.Content(viewEnvironment)
    }
  }
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
