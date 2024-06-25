package com.squareup.workflow1.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Renders [rendering] into the composition using this [ViewEnvironment]'s
 * [ScreenComposeFactoryFinder] to generate the view.
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
 * @param rendering The workflow rendering to display.
 * @param modifier A [Modifier] that will be applied to composable used to show [rendering].
 *
 * @throws IllegalArgumentException if no factory can be found for [rendering]'s type.
 */
@WorkflowUiExperimentalApi
@Composable
public fun WorkflowRendering(
  rendering: Screen,
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
        composableFactory.Content(rendering, viewEnvironment)
      }
    }
  }
}

/**
 * Returns a [LifecycleOwner] that is a mirror of the current [LocalLifecycleOwner] until this
 * function leaves the composition. More details can be found [here](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-lifecycle.html)
 * for the lifecycle of a composable function depending what platform is used
 */
@Composable private fun rememberChildLifecycleOwner(): LifecycleOwner {
  val lifecycleOwner = remember {
    object : LifecycleOwner {
      val registry = LifecycleRegistry(this)
      override val lifecycle: Lifecycle
        get() = registry
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
      if (lifecycleOwner.registry.currentState != INITIALIZED) {
        lifecycleOwner.registry.currentState = DESTROYED
      }
    }
  }

  return lifecycleOwner
}
