package com.squareup.workflow1.ui.backstack

import android.os.Bundle
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

  private var restored = false
  private val controller = SavedStateRegistryController.create(this)

  override fun getSavedStateRegistry(): SavedStateRegistry = controller.savedStateRegistry

  /** See [SavedStateRegistryController.performSave] */
  fun performSave(outBundle: Bundle) {
    controller.performSave(outBundle)
  }

  /** See [SavedStateRegistryController.performRestore] */
  fun performRestore(savedState: Bundle?) {
    restored = true
    controller.performRestore(savedState)
  }

  /**
   * Calls [performRestore] with a null [Bundle] if it has not been called yet.
   */
  fun ensureRestored() {
    if (!restored) {
      performRestore(null)
    }
  }
}
