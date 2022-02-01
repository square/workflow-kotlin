package com.squareup.workflow1.ui.androidx

import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * The implementation of [SavedStateRegistryOwner] that should be installed on every immediate child
 * view of container views so that when a view inside a container calls
 * [ViewTreeSavedStateRegistryOwner.get] on itself, one of these is returned.
 *
 * Internally, this class exposes a [controller] to allow saving and restoring the child view's
 * state, e.g. to/from a [StateRegistryAggregator].
 *
 * To create an instance, call [installAsSavedStateRegistryOwnerOn].
 *
 * @param key The key used to save and restore this controller from a [SavedStateRegistry].
 * @param lifecycleOwner The [LifecycleOwner] that will be delegated to by this instance. Note that
 * [SavedStateRegistryOwner] extends [LifecycleOwner].
 */
@WorkflowUiExperimentalApi
public class KeyedStateRegistryOwner private constructor(
  public val key: String,
  lifecycleOwner: LifecycleOwner
) : SavedStateRegistryOwner, LifecycleOwner by lifecycleOwner {
  public val controller: SavedStateRegistryController = SavedStateRegistryController.create(this)
  override fun getSavedStateRegistry(): SavedStateRegistry = controller.savedStateRegistry

  public companion object {
    /**
     * Creates a new [KeyedStateRegistryOwner] that is bound to [view]'s [WorkflowLifecycleOwner]
     * and sets it as the [ViewTreeSavedStateRegistryOwner] on [view].
     *
     * Note that, once installed, the owner does not need to be "uninstalled". It can live on the
     * view instance as long as it exists, and just be garbage-collected with the view.
     */
    public fun installAsSavedStateRegistryOwnerOn(
      view: View,
      key: String
    ): KeyedStateRegistryOwner {
      val lifecycleOwner = checkNotNull(WorkflowLifecycleOwner.get(view)) {
        "Expected back stack container view to set a WorkflowLifecycleOwner on its immediate " +
          "child views."
      }
      val registryOwner = KeyedStateRegistryOwner(key, lifecycleOwner)
      ViewTreeSavedStateRegistryOwner.set(view, registryOwner)
      return registryOwner
    }
  }
}
