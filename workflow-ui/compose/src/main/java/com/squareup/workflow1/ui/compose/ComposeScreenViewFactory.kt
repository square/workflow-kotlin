// See https://youtrack.jetbrains.com/issue/KT-31734
@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass

/**
 * Creates a [ScreenViewFactory] that uses a [Composable] function to display the rendering.
 *
 * Simple usage:
 *
 * ```
 * val FooViewFactory = composeScreenViewFactory { rendering, _ ->
 *   Text(rendering.message)
 * }
 *
 * …
 *
 * val viewRegistry = ViewRegistry(FooViewFactory, …)
 * ```
 *
 * If you need to write a class instead of a function, for example to support dependency injection,
 * see [ComposeScreenViewFactory].
 *
 * For more details about how to write composable view factories, see [ComposeScreenViewFactory].
 */
@WorkflowUiExperimentalApi
public inline fun <reified RenderingT : Screen> composeScreenViewFactory(
  noinline content: @Composable (
    rendering: RenderingT,
    environment: ViewEnvironment
  ) -> Unit
): ScreenViewFactory<RenderingT> = composeScreenViewFactory(RenderingT::class, content)

@PublishedApi
@WorkflowUiExperimentalApi
internal fun <RenderingT : Screen> composeScreenViewFactory(
  type: KClass<in RenderingT>,
  content: @Composable (
    rendering: RenderingT,
    environment: ViewEnvironment
  ) -> Unit
): ScreenViewFactory<RenderingT> = object : ComposeScreenViewFactory<RenderingT>() {
  override val type: KClass<in RenderingT> = type

  @Composable override fun Content(
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  ) {
    content(rendering, viewEnvironment)
  }
}

/**
 * A [ScreenViewFactory] that uses a [Composable] function to display the rendering. It is the
 * Compose-based analogue of [ScreenViewRunner][com.squareup.workflow1.ui.ScreenViewRunner].
 *
 * Simple usage:
 *
 * ```
 * class FooViewFactory : ComposeScreenViewFactory<FooScreen>() {
 *   override val type = FooScreen::class
 *
 *   @Composable override fun Content(
 *     rendering: FooScreen,
 *     viewEnvironment: ViewEnvironment
 *   ) {
 *     Text(rendering.message)
 *   }
 * }
 *
 * …
 *
 * val viewRegistry = ViewRegistry(FooViewFactory, …)
 * ```
 *
 * ## Nesting child renderings
 *
 * Workflows can render other workflows, and renderings from one workflow can contain renderings
 * from other workflows. These renderings may all be bound to their own [ScreenViewFactory]s.
 * Regular [ScreenViewFactory]s and [ScreenViewRunner][com.squareup.workflow1.ui.ScreenViewRunner]s
 * use [WorkflowViewStub][com.squareup.workflow1.ui.WorkflowViewStub] to recursively show nested
 * renderings using the [ViewRegistry][com.squareup.workflow1.ui.ViewRegistry].
 *
 * View factories defined using this function may also show nested renderings. Doing so is as simple
 * as calling [WorkflowRendering] and passing in the nested rendering. See the kdoc on that function
 * for an example.
 *
 * Nested renderings will have access to any
 * [composition locals][androidx.compose.runtime.CompositionLocal] defined in outer composable, even
 * if there are legacy views in between them, as long as the [ViewEnvironment] is propagated
 * continuously between the two factories.
 *
 * ## Initializing Compose context
 *
 * Often all the [composeScreenViewFactory] factories in an app need to share some context –
 * for example, certain composition locals need to be provided, such as `MaterialTheme`.
 * To configure this shared context, call [withCompositionRoot] on your top-level [ViewEnvironment].
 * The first time a [composeViewFactory] is used to show a rendering, its [Content] function will
 * be wrapped with the [CompositionRoot]. See the documentation on [CompositionRoot] for
 * more information.
 */
@WorkflowUiExperimentalApi
public abstract class ComposeScreenViewFactory<RenderingT : Screen> :
  ScreenViewFactory<RenderingT> {
  /**
   * The composable content of this [ScreenViewFactory]. This method will be called
   * any time [rendering] or [viewEnvironment] change. It is the Compose-based analogue of
   * [ScreenViewRunner.showRendering][com.squareup.workflow1.ui.ScreenViewRunner.show].
   */
  @Composable public abstract fun Content(
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  )

  final override fun buildView(
    initialRendering: RenderingT,
    initialEnvironment: ViewEnvironment,
    context: Context,
    container: ViewGroup?
  ): ScreenViewHolder<RenderingT> {
    val view = ComposeView(context)
    return ScreenViewHolder<RenderingT>(initialEnvironment, view) { rendering, environment ->
      // Update the state whenever a new rendering is emitted.
      // This lambda will be executed synchronously before ScreenViewHolder.show returns.
      view.setContent { Content(rendering, environment) }
    }
  }
}
