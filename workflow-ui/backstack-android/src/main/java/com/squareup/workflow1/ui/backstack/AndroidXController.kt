package com.squareup.workflow1.ui.backstack

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * TODO write documentation
 */
internal class AndroidXController(
  savedState: Bundle?
) : LifecycleOwner, SavedStateRegistryOwner {

  private val lifecycleRegistry = LifecycleRegistry(this)
  private val savedStateController = SavedStateRegistryController.create(this).apply {
    // Must be called after lifecycleRegistry is initialized.
    performRestore(savedState)
  }

  override fun getLifecycle(): Lifecycle = lifecycleRegistry
  override fun getSavedStateRegistry(): SavedStateRegistry = savedStateController.savedStateRegistry

  fun startLifecycle() {
    check(lifecycleRegistry.currentState == INITIALIZED)
    lifecycleRegistry.currentState = STARTED
  }

  fun resumeLifecycle() {
    check(lifecycleRegistry.currentState == STARTED)
    lifecycleRegistry.currentState = RESUMED
  }

  fun destroyLifecycle() {
    lifecycleRegistry.currentState = DESTROYED
  }

  fun performSave(): Bundle = Bundle().also(savedStateController::performSave)
}
