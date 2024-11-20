package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * Returns a [LifecycleOwner] that is a mirror of the current [LocalLifecycleOwner] until this
 * function leaves the composition. Similar to [WorkflowLifecycleOwner] for views, but a
 * bit simpler since we don't need to worry about attachment state.
 */
@Composable internal fun rememberChildLifecycleOwner(
  parentLifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle
): LifecycleOwner {
  val owner = remember {
    ComposeLifecycleOwner.installOn(
      initialParentLifecycle = parentLifecycle
    )
  }
  val lifecycleOwner = remember(parentLifecycle) {
    owner.apply { updateParentLifecycle(parentLifecycle) }
  }
  return lifecycleOwner
}

/**
 * A custom [LifecycleOwner] that synchronizes its lifecycle with a parent [Lifecycle] and
 * integrates with Jetpack Compose's lifecycle through [RememberObserver].
 *
 * ## Purpose
 *
 * - Ensures that any lifecycle-aware components within a composable function have a lifecycle that
 *   accurately reflects both the parent lifecycle and the composable's own lifecycle.
 * - Manages lifecycle transitions and observer registration/removal to prevent memory leaks and
 *   ensure proper cleanup when the composable leaves the composition.
 *
 * ## Key Features
 *
 * - Lifecycle Synchronization: Mirrors lifecycle events from the provided `parentLifecycle` to
 *   its own [LifecycleRegistry], ensuring consistent state transitions.
 * - Compose Integration: Implements [RememberObserver] to align with the composable's lifecycle
 *   in the Compose memory model.
 * - Automatic Observer Management: Adds and removes a [LifecycleEventObserver] to the parent
 *   lifecycle, preventing leaks and ensuring proper disposal.
 * - **State Transition Safety:** Carefully manages lifecycle state changes to avoid illegal
 *   transitions, especially during destruction.
 *
 * ## Usage Notes
 *
 * - Should be used in conjunction with `remember` and provided the `parentLifecycle` as a key to
 *   ensure it updates correctly when the parent lifecycle changes.
 * - By integrating with Compose's lifecycle, it ensures that resources are properly released when
 *   the composable leaves the composition.
 *
 * @param initialParentLifecycle The parent [Lifecycle] with which this lifecycle owner should
 * synchronize with initially. If new parent lifecycles are provided, they should be passed to
 * [updateParentLifecycle].
 */
private class ComposeLifecycleOwner(
  initialParentLifecycle: Lifecycle
) : LifecycleOwner, RememberObserver, LifecycleEventObserver {

  private var parentLifecycle: Lifecycle = initialParentLifecycle

  private val registry = LifecycleRegistry(this)
  override val lifecycle: Lifecycle
    get() = registry

  override fun onRemembered() {
  }

  override fun onAbandoned() {
    onForgotten()
  }

  override fun onForgotten() {
    parentLifecycle.removeObserver(this)

    // If we're leaving the composition, ensure the lifecycle is cleaned up
    if (registry.currentState != Lifecycle.State.INITIALIZED) {
      registry.currentState = Lifecycle.State.DESTROYED
    }
  }

  fun updateParentLifecycle(lifecycle: Lifecycle) {
    parentLifecycle.removeObserver(this)
    parentLifecycle = lifecycle
    parentLifecycle.addObserver(this)
  }

  override fun onStateChanged(
    source: LifecycleOwner,
    event: Event
  ) {
    registry.handleLifecycleEvent(event)
  }

  companion object {
    fun installOn(initialParentLifecycle: Lifecycle): ComposeLifecycleOwner {
      return ComposeLifecycleOwner(initialParentLifecycle).also {
        // We need to synchronize the lifecycles before the child ever even sees the lifecycle
        // because composes contract tries to guarantee that the lifecycle is in at least the
        // CREATED state by the time composition is actually running. If we don't synchronize
        // the lifecycles right away, then we break that invariant. One concrete case of this is
        // that SavedStateRegistry requires its lifecycle to be CREATED before reading values
        // from it, and consuming values from an SSR is a valid thing to do from composition
        // directly, and in fact AndroidComposeView itself does this.
        initialParentLifecycle.addObserver(it)
      }
    }
  }
}
