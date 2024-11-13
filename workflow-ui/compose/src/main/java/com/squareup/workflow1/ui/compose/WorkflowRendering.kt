package com.squareup.workflow1.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner

/**
 * Renders [rendering] into the composition using the [ViewEnvironment] found in
 * [LocalWorkflowEnvironment] to source a [ScreenComposableFactoryFinder] to generate
 * the view.
 *
 * This function fulfills a similar role as [ScreenViewHolder] and [WorkflowViewStub],
 * but is much more convenient to use from Composable functions. Note that,
 * just as with [ScreenViewHolder] and [WorkflowViewStub], it doesn't matter whether
 * the factory registered for the rendering is using classic Android views or Compose.
 *
 * ## Example
 *
 *     data class FramedRendering<R : Any>(
 *       val borderColor: Color,
 *       val child: R
 *     ) : ComposeRendering {
 *
 *       @Composable override fun Content() {
 *         Surface(border = Border(borderColor, 8.dp)) {
 *           WorkflowRendering(child)
 *         }
 *       }
 *     }
 *
 * @param rendering The workflow rendering to display.
 * @param modifier A [Modifier] that will be applied to composable used to show [rendering].
 *
 * @throws IllegalArgumentException if no factory can be found for [rendering]'s type.
 */
@WorkflowUiExperimentalApi
@Composable
public fun WorkflowRendering(
  rendering: Screen,
  modifier: Modifier = Modifier
) {
  // This will fetch a new view factory any time the new rendering is incompatible with the previous
  // one, as determined by Compatible. This corresponds to WorkflowViewStub's canShowRendering
  // check.
  val renderingCompatibilityKey = Compatible.keyFor(rendering)
  val viewEnvironment = LocalWorkflowEnvironment.current

  // By surrounding the below code with this key function, any time the new rendering is not
  // compatible with the previous rendering we'll tear down the previous subtree of the composition,
  // including its lifecycle, which destroys the lifecycle and any remembered state. If the view
  // factory created an Android view, this will also remove the old one from the view hierarchy
  // before replacing it with the new one.
  key(renderingCompatibilityKey) {
    val composableFactory = remember {
      // The view registry may return a new factory instance for a rendering every time we ask it, for
      // example if an AndroidScreen doesn't share its factory between rendering instances. We
      // intentionally don't ask it for a new instance every time to match the behavior of
      // WorkflowViewStub and other containers, which only ask for a new factory when the rendering is
      // incompatible.
      rendering.toComposableFactory(viewEnvironment)
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
        composableFactory.Content(rendering)
      }
    }
  }
}

/**
 * Provides a [LifecycleOwner] for child composables that mirrors the lifecycle of the nearest
 * ancestor view’s [LifecycleOwner]. This function helps maintain lifecycle consistency by
 * leveraging the lifecycle of the current [LocalView], ensuring the lifecycle of this composable
 * is synchronized with the view hierarchy.
 *
 * Unlike standalone lifecycle owners, this function connects directly to the existing view’s
 * lifecycle, minimizing potential mismatches that can occur when creating independent lifecycles
 * within a composable. It is particularly useful when managing UI that needs to stay in sync with
 * the host view’s lifecycle.
 *
 * The returned [LifecycleOwner] uses the parent view’s lifecycle if available. When the parent
 * lifecycle state changes, this owner reflects those changes. If the view lifecycle is not found,
 * a new [LifecycleRegistry] is created, though this is less common.
 *
 * This function:
 * - Observes the [LocalLifecycleOwner]’s lifecycle, forwarding events to ensure accurate state
 *   propagation.
 * - Ensures proper cleanup by marking the lifecycle as [Lifecycle.State.DESTROYED] upon
 *   composition exit, if it hasn’t already been initialized.
 *
 * @return A [LifecycleOwner] that reflects the lifecycle of the view hierarchy for child
 * composables.
 */
@Composable private fun rememberChildLifecycleOwner(): LifecycleOwner {
  val view = LocalView.current
  val lifecycleOwner = remember {
    val owner = view.findViewTreeLifecycleOwner()
    object : LifecycleOwner {
      val registry = LifecycleRegistry(owner ?: this)
      override val lifecycle: Lifecycle
        get() = owner?.lifecycle ?: registry
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
      // that we can't transition from INITIALIZED to DESTROYED – the LifecycleRegistry will throw.
      // WorkflowLifecycleOwner has this same check.
      if (lifecycleOwner.registry.currentState != INITIALIZED) {
        lifecycleOwner.registry.currentState = DESTROYED
      }
    }
  }

  return lifecycleOwner
}
