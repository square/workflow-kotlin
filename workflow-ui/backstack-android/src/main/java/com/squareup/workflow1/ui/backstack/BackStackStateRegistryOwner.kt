package com.squareup.workflow1.ui.backstack

import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * An implementation of [SavedStateRegistryOwner] that also exposes its [controller].
 * Intended to be set as the `ViewTreeSavedStateRegistryOwner` on child views.
 */
internal class BackStackStateRegistryOwner(
  lifecycleOwner: LifecycleOwner
) : SavedStateRegistryOwner, LifecycleOwner by lifecycleOwner {
  val controller = SavedStateRegistryController.create(this)
  override fun getSavedStateRegistry(): SavedStateRegistry = controller.savedStateRegistry
}
