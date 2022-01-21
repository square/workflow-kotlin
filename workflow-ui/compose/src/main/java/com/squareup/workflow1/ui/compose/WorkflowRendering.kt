package com.squareup.workflow1.ui.compose

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.getFactoryForRendering
import com.squareup.workflow1.ui.getShowRendering
import com.squareup.workflow1.ui.showRendering
import com.squareup.workflow1.ui.start
import kotlin.reflect.KClass

/**
 * Renders [rendering] into the composition using this [ViewEnvironment]'s [ViewRegistry] to
 * generate the view.
 *
 * This function fulfills a similar role as [WorkflowViewStub], but is much more convenient to use
 * from Composable functions. Note, however, that just like [WorkflowViewStub], it doesn't matter
 * whether the factory registered for the rendering is using classic Android views or Compose.
 *
 * ## Example
 *
 * ```
 * data class FramedRendering<R : Any>(
 *   val borderColor: Color,
 *   val child: R
 * ) : ComposeRendering {
 *
 *   @Composable override fun Content(viewEnvironment: ViewEnvironment) {
 *     Surface(border = Border(borderColor, 8.dp)) {
 *       WorkflowRendering(child, viewEnvironment)
 *     }
 *   }
 * }
 * ```
 *
 * @param rendering The workflow rendering to display. May be of any type for which a [ViewFactory]
 * has been registered in [viewEnvironment]'s [ViewRegistry].
 * @param modifier A [Modifier] that will be applied to composable used to show [rendering].
 *
 * @throws IllegalArgumentException if no factory can be found for [rendering]'s type.
 */
@WorkflowUiExperimentalApi
@Composable public fun WorkflowRendering(
  rendering: Any,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier
) {
  // This will fetch a new view factory any time the new rendering is incompatible with the previous
  // one, as determined by Compatible. This corresponds to WorkflowViewStub's canShowRendering
  // check.
  val renderingCompatibilityKey = Compatible.keyFor(rendering)

  // By surrounding the below code with this key function, any time the new rendering is not
  // compatible with the previous rendering we'll tear down the previous subtree of the composition,
  // including its lifecycle, which destroys the lifecycle and any remembered state. If the view
  // factory created an Android view, this will also remove the old one from the view hierarchy
  // before replacing it with the new one.
  key(renderingCompatibilityKey) {
    val viewFactory = remember {
      // The view registry may return a new factory instance for a rendering every time we ask it, for
      // example if an AndroidViewRendering doesn't share its factory between rendering instances. We
      // intentionally don't ask it for a new instance every time to match the behavior of
      // WorkflowViewStub and other containers, which only ask for a new factory when the rendering is
      // incompatible.
      viewEnvironment[ViewRegistry]
        // Can't use ViewRegistry.buildView here since we need the factory to convert it to a
        // compose one.
        .getFactoryForRendering(rendering)
        .asComposeViewFactory()
    }

    // Just like WorkflowViewStub, we need to manage a Lifecycle for the child view. We just provide
    // a local here – ViewFactoryAndroidView will handle setting the appropriate view tree owners
    // on the child view when necessary. Because this function is surrounded by a key() call, when
    // the rendering is incompatible, the lifecycle for the old view will be destroyed.
    val lifecycleOwner = rememberChildLifecycleOwner()

    CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
      // We need to propagate min constraints because one of the likely uses for the modifier passed
      // into this function is to directly control the layout of the child view – which means
      // minimum constraints are likely to be significant.
      Box(modifier, propagateMinConstraints = true) {
        viewFactory.Content(rendering, viewEnvironment)
      }
    }
  }
}

/**
 * Returns a [LifecycleOwner] that is a mirror of the current [LocalLifecycleOwner] until this
 * function leaves the composition. Similar to [WorkflowLifecycleOwner] for views, but a
 * bit simpler since we don't need to worry about attachment state.
 */
@Composable private fun rememberChildLifecycleOwner(): LifecycleOwner {
  val lifecycleOwner = remember {
    object : LifecycleOwner {
      val registry = LifecycleRegistry(this)
      override fun getLifecycle(): Lifecycle = registry
    }
  }
  val parentLifecycle = LocalLifecycleOwner.current.lifecycle

  DisposableEffect(parentLifecycle) {
    val parentObserver = LifecycleEventObserver { _, event ->
      // Any time the parent lifecycle changes state, perform the same change on our lifecycle.
      lifecycleOwner.registry.handleLifecycleEvent(event)
    }

    parentLifecycle.addObserver(parentObserver)
    onDispose {
      parentLifecycle.removeObserver(parentObserver)

      // If we're leaving the composition it means the WorkflowRendering is either going away itself
      // or about to switch to an incompatible rendering – either way, this lifecycle is dead. Note
      // that we can't transition from INITIALIZED to DESTROYED – the LifecycelRegistry will throw.
      // WorkflowLifecycleOwner has this same check.
      if (lifecycleOwner.registry.currentState != INITIALIZED) {
        lifecycleOwner.registry.currentState = DESTROYED
      }
    }
  }

  return lifecycleOwner
}

/**
 * Returns a [ComposeViewFactory] that makes it convenient to display this [ViewFactory] as a
 * composable. If this is a [ComposeViewFactory] already it just returns `this`, otherwise it wraps
 * the factory in one that manages a classic Android view.
 */
@OptIn(WorkflowUiExperimentalApi::class)
private fun <R : Any> ViewFactory<R>.asComposeViewFactory() =
  (this as? ComposeViewFactory) ?: object : ComposeViewFactory<R>() {

    private val originalFactory = this@asComposeViewFactory
    override val type: KClass<in R> get() = originalFactory.type

    /**
     * This is effectively the logic of [WorkflowViewStub], but translated into Compose idioms.
     * This approach has a few advantages:
     *
     *  - Avoids extra custom views required to host `WorkflowViewStub` inside a Composition. Its trick
     *    of replacing itself in its parent doesn't play nicely with Compose.
     *  - Allows us to pass the correct parent view for inflation (the root of the composition).
     *  - Avoids `WorkflowViewStub` having to do its own lookup to find the correct [ViewFactory], since
     *    we already have the correct one.
     *  - Propagate the current [LifecycleOwner] from [LocalLifecycleOwner] by setting it as the
     *    [ViewTreeLifecycleOwner] on the view.
     *
     * Like `WorkflowViewStub`, this function uses the [originalFactory] to create and memoize a
     * [View] to display the [rendering], keeps it updated with the latest [rendering] and
     * [viewEnvironment], and adds it to the composition.
     */
    @Composable override fun Content(
      rendering: R,
      viewEnvironment: ViewEnvironment
    ) {
      val lifecycleOwner = LocalLifecycleOwner.current

      AndroidView(
        factory = { context ->
          // We pass in a null container because the container isn't a View, it's a composable. The
          // compose machinery will generate an intermediate view that it ends up adding this to but
          // we don't have access to that.
          originalFactory.buildView(rendering, viewEnvironment, context, container = null)
            .also { view ->
              view.start()

              // Mirrors the check done in ViewRegistry.buildView.
              checkNotNull(view.getShowRendering<Any>()) {
                "View.bindShowRendering should have been called for $view, typically by the " +
                  "${ViewFactory::class.java.name} that created it."
              }

              // Unfortunately AndroidView doesn't propagate this itself.
              ViewTreeLifecycleOwner.set(view, lifecycleOwner)
              // We don't propagate the (non-compose) SavedStateRegistryOwner, or the (compose)
              // SaveableStateRegistry, because currently all our navigation is implemented as
              // Android views, which ensures there is always an Android view between any state
              // registry and any Android view shown as a child of it, even if there's a compose
              // view in between.
            }
        },
        // This function will be invoked every time this composable is recomposed, which means that
        // any time a new rendering or view environment are passed in we'll send them to the view.
        update = { view ->
          view.showRendering(rendering, viewEnvironment)
        }
      )
    }
  }
