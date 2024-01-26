package com.squareup.workflow1.ui.compose

import android.content.Context
import android.view.ViewGroup
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.ViewRegistry.Key
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.OnBackPressedDispatcherOwnerKey
import com.squareup.workflow1.ui.show
import com.squareup.workflow1.ui.startShowing
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

/**
 * Convert a [ScreenComposableFactory] into a [ScreenViewFactory]
 * by using a [ComposeView] to host [ScreenComposableFactory.Content].
 *
 * It is unusual to use this function directly, it is mainly an implementation detail
 * of [ViewEnvironment.withComposeInteropSupport].
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenComposableFactory<ScreenT>.asViewFactory():
  ScreenViewFactory<ScreenT> {

  return object : ScreenViewFactory<ScreenT> {
    override val type = this@asViewFactory.type

    override fun buildView(
      initialRendering: ScreenT,
      initialEnvironment: ViewEnvironment,
      context: Context,
      container: ViewGroup?
    ): ScreenViewHolder<ScreenT> {
      val view = ComposeView(context)
      return ScreenViewHolder(initialEnvironment, view) { newRendering, environment ->
        // Update the state whenever a new rendering is emitted.
        // This lambda will be executed synchronously before ScreenViewHolder.show returns.
        view.setContent { Content(newRendering, environment) }
      }
    }
  }
}

/**
 * Convert a [ScreenViewFactory] to a [ScreenComposableFactory],
 * using [AndroidView] to host the `View` it builds.
 *
 * It is unusual to use this function directly, it is mainly an implementation detail
 * of [ViewEnvironment.withComposeInteropSupport].
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewFactory<ScreenT>.asComposableFactory():
  ScreenComposableFactory<ScreenT> {
  return object : ScreenComposableFactory<ScreenT> {
    private val viewFactory = this@asComposableFactory

    override val type: KClass<in ScreenT> get() = viewFactory.type

    /**
     * This is effectively the logic of `WorkflowViewStub`, but translated into Compose idioms.
     * This approach has a few advantages:
     *
     *  - Avoids extra custom views required to host `WorkflowViewStub` inside a Composition. Its trick
     *    of replacing itself in its parent doesn't play nicely with Compose.
     *  - Allows us to pass the correct parent view for inflation (the root of the composition).
     *  - Avoids `WorkflowViewStub` having to do its own lookup to find the correct
     *    [ScreenViewFactory], since we already have the correct one.
     *  - Propagate the current `LifecycleOwner` from [LocalLifecycleOwner] by setting it as the
     *    [ViewTreeLifecycleOwner] on the view.
     *  - Propagate the current [OnBackPressedDispatcherOwner] from either
     *    [LocalOnBackPressedDispatcherOwner] or the [viewEnvironment],
     *    both on the [AndroidView] via [setViewTreeOnBackPressedDispatcherOwner],
     *    and in the [ViewEnvironment] for use by any nested [WorkflowViewStub]
     *
     * Like `WorkflowViewStub`, this function uses the [viewFactory] to create and memoize a
     * `View` to display the [rendering], keeps it updated with the latest [rendering] and
     * [environment], and adds it to the composition.
     */
    @Composable override fun Content(
      rendering: ScreenT,
      environment: ViewEnvironment
    ) {
      val lifecycleOwner = LocalLifecycleOwner.current

      // Make sure any nested WorkflowViewStub will be able to propagate the
      // OnBackPressedDispatcherOwner, if we found one. No need to fail fast here.
      // It's only an issue if someone tries to use it, and the error message
      // at those call sites should be clear enough.
      val onBackOrNull = LocalOnBackPressedDispatcherOwner.current
        ?: environment.map[OnBackPressedDispatcherOwnerKey] as? OnBackPressedDispatcherOwner

      val envWithOnBack = onBackOrNull
        ?.let { environment + (OnBackPressedDispatcherOwnerKey to it) }
        ?: environment

      AndroidView(
        factory = { context ->

          // We pass in a null container because the container isn't a View, it's a composable. The
          // compose machinery will generate an intermediate view that it ends up adding this to but
          // we don't have access to that.
          viewFactory
            .startShowing(rendering, envWithOnBack, context, container = null)
            .let { viewHolder ->
              // Put the viewHolder in a tag so that we can find it in the update lambda, below.
              viewHolder.view.setTag(R.id.workflow_screen_view_holder, viewHolder)

              // Unfortunately AndroidView doesn't propagate these itself.
              viewHolder.view.setViewTreeLifecycleOwner(lifecycleOwner)
              onBackOrNull?.let {
                viewHolder.view.setViewTreeOnBackPressedDispatcherOwner(it)
              }

              // We don't propagate the (non-compose) SavedStateRegistryOwner, or the (compose)
              // SaveableStateRegistry, because currently all our navigation is implemented as
              // Android views, which ensures there is always an Android view between any state
              // registry and any Android view shown as a child of it, even if there's a compose
              // view in between.
              viewHolder.view
            }
        },
        // This function will be invoked every time this composable is recomposed, which means that
        // any time a new rendering or view environment are passed in we'll send them to the view.
        update = { view ->
          @Suppress("UNCHECKED_CAST")
          val viewHolder =
            view.getTag(R.id.workflow_screen_view_holder) as ScreenViewHolder<ScreenT>
          viewHolder.show(rendering, envWithOnBack)
        }
      )
    }
  }
}
