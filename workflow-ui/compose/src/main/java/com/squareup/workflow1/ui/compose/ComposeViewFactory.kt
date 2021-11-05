// See https://youtrack.jetbrains.com/issue/KT-31734
@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry", "DEPRECATION")

package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.bindShowRendering
import kotlin.reflect.KClass

/**
 * Creates a [ViewFactory] that uses a [Composable] function to display the rendering.
 *
 * Simple usage:
 *
 * ```
 * val FooViewFactory = composeViewFactory { rendering, _ ->
 *   Text(rendering.message)
 * }
 *
 * …
 *
 * val viewRegistry = ViewRegistry(FooViewFactory, …)
 * ```
 *
 * If you need to write a class instead of a function, for example to support dependency injection,
 * see [ComposeViewFactory].
 *
 * For more details about how to write composable view factories, see [ComposeViewFactory].
 */
@WorkflowUiExperimentalApi
public inline fun <reified RenderingT : Any> composeViewFactory(
  noinline content: @Composable (
    rendering: RenderingT,
    environment: ViewEnvironment
  ) -> Unit
): ViewFactory<RenderingT> = composeViewFactory(RenderingT::class, content)

@PublishedApi
@WorkflowUiExperimentalApi
internal fun <RenderingT : Any> composeViewFactory(
  type: KClass<RenderingT>,
  content: @Composable (
    rendering: RenderingT,
    environment: ViewEnvironment
  ) -> Unit
): ViewFactory<RenderingT> = object : ComposeViewFactory<RenderingT>() {
  override val type: KClass<in RenderingT> = type
  @Composable override fun Content(
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  ) {
    content(rendering, viewEnvironment)
  }
}

/**
 * A [ViewFactory] that uses a [Composable] function to display the rendering. It is the
 * Compose-based analogue of [LayoutRunner].
 *
 * Simple usage:
 *
 * ```
 * class FooViewFactory : ComposeViewFactory<Foo>() {
 *   override val type = Foo::class
 *
 *   @Composable override fun Content(
 *     rendering: Foo,
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
 * from other workflows. These renderings may all be bound to their own [ViewFactory]s. Regular
 * [ViewFactory]s and [LayoutRunner]s use
 * [WorkflowViewStub][com.squareup.workflow1.ui.WorkflowViewStub] to recursively show nested
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
 * Often all the [composeViewFactory] factories in an app need to share some context – for example,
 * certain composition locals need to be provided, such as `MaterialTheme`. To configure this shared
 * context, call [withCompositionRoot] on your top-level [ViewEnvironment]. The first time a
 * [composeViewFactory] is used to show a rendering, its [Content] function will be wrapped
 * with the [CompositionRoot]. See the documentation on [CompositionRoot] for more information.
 */
@WorkflowUiExperimentalApi
public abstract class ComposeViewFactory<RenderingT : Any> : ViewFactory<RenderingT> {

  /**
   * The composable content of this [ViewFactory]. This method will be called any time [rendering]
   * or [viewEnvironment] change. It is the Compose-based analogue of [LayoutRunner.showRendering].
   */
  @Composable public abstract fun Content(
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  )

  final override fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View = ComposeView(contextForNewView).also { composeView ->
    // Update the state whenever a new rendering is emitted.
    // This lambda will be executed synchronously before bindShowRendering returns.
    composeView.bindShowRendering(
      initialRendering,
      initialViewEnvironment
    ) { rendering, environment ->
      // Entry point to the world of Compose.
      composeView.setContent {
        Content(rendering, environment)
      }
    }
  }
}
