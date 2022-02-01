package com.squareup.workflow1.ui.container

import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.container.KeyedStateRegistryOwner.Companion.installAsSavedStateRegistryOwnerOn

/**
 * The implementation of [SavedStateRegistryOwner] that is installed on every immediate child view
 * of a [BackStackContainer]. In other words, when a view inside a [BackStackContainer] calls
 * [ViewTreeSavedStateRegistryOwner.get] on itself, one of these is returned.
 *
 * Internally, this class exposes a [controller] to allow the [ViewStateCache] to save and restore
 * any state registered by child views.
 *
 * To create an instance, call [installAsSavedStateRegistryOwnerOn].
 *
 * @param key The key used to save and restore this controller from a [SavedStateRegistry].
 * @param lifecycleOwner The [LifecycleOwner] that will be delegated to by this instance. Note that
 * [SavedStateRegistryOwner] extends [LifecycleOwner].
 */
internal class KeyedStateRegistryOwner private constructor(
  val key: String,
  lifecycleOwner: LifecycleOwner
) : SavedStateRegistryOwner, LifecycleOwner by lifecycleOwner {
  val controller = SavedStateRegistryController.create(this)
  override fun getSavedStateRegistry(): SavedStateRegistry = controller.savedStateRegistry

  companion object {
    /**
     * Creates a new [KeyedStateRegistryOwner] that is bound to [view]'s [WorkflowLifecycleOwner]
     * and sets it as the [ViewTreeSavedStateRegistryOwner] on [view].
     *
     * Note that, once installed, the owner does not need to be "uninstalled". It can live on the
     * view instance as long as it exists, and just be garbage-collected with the view.
     */
    @OptIn(WorkflowUiExperimentalApi::class)
    fun installAsSavedStateRegistryOwnerOn(
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
