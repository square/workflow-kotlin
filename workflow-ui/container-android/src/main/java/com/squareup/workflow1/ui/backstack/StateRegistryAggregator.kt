package com.squareup.workflow1.ui.backstack

import android.os.Bundle
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Manages a group of [SavedStateRegistryController]s that are all saved to and restored from a single
 * "parent" [SavedStateRegistryOwner].
 *
 * This class is designed to support a navigation container that owns a
 * [SavedStateRegistryController] for each of a set of navigation "frames", where a frame is
 * something that can be navigated to/from. A frame loosely consists of a [View][android.view.View]
 * as well as a [SavedStateRegistryOwner]/[SavedStateRegistryController] that is set on that view
 * via [androidx.savedstate.ViewTreeSavedStateRegistryOwner]. The view associated with the
 * controller is considered the "owning" view.
 *
 * To save a child registry to the parent registry, call [saveRegistryController]. This should be
 * done whenever the "navigation frame" associated with the child registry is about to be hidden,
 * and from the [onWillSave] callback. To restore a child registry, call
 * [restoreRegistryControllerIfReady]. This should be done when a navigation frame that was
 * previously hidden is being shown again, and from the [onRestored] callback. See the kdoc on these
 * methods and callbacks for more information.
 *
 * [attachToParentRegistry] should be called when the owning view is attached to a window, and
 * passed the parent registry. [detachFromParentRegistry] should be called when the view is
 * detached. See the kdoc on those methods for more information about what they do.
 *
 * @param onWillSave Called whenever the parent registry is performing a save operation. Provides an
 * opportunity to save any active child registries via [saveRegistryController].
 * @param onRestored Called as soon as this instance has been restored from a parent registry, after
 * [attachToParentRegistry] and the parent registry's lifecycle is in the `CREATED` state.
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal class StateRegistryAggregator(
  private val onWillSave: (StateRegistryAggregator) -> Unit,
  private val onRestored: (StateRegistryAggregator) -> Unit,
) {

  /**
   * Holds any states restored from the parent registry, as well as any states saved after that time
   * via [saveRegistryController].
   *
   * Will be null until we are restored from the parent registry. After being restored, it will
   * never be re-assigned again.
   */
  private var states: MutableMap<String, Bundle>? = null

  private val isRestored get() = states != null

  /** Memoizes the registry owner passed to [attachToParentRegistry] so it can be detached later. */
  private var parentRegistryOwner: SavedStateRegistryOwner? = null
  private var parentKey: String? = null

  /**
   * Used to observe the parent registry's lifecycle to know when it becomes `CREATED` and is ready
   * for us to restore ourselves. This observer is only registered between calls to
   * [attachToParentRegistry] and [detachFromParentRegistry], and will only be registered if this
   * instance has not already been restored.
   */
  private val lifecycleObserver = object : LifecycleEventObserver {
    override fun onStateChanged(
      source: LifecycleOwner,
      event: Event
    ) {
      // We should always get all the events required to bring this observer from it's initial state
      // (INITIALIZED) up to the current state, as per the contract of Lifecycle. But double-check
      // here just in case we're dealing with a bad implementation, so that this state machine
      // doesn't hang forever in a bad state.
      check(event == ON_CREATE) {
        "Expected to receive ON_CREATE event before anything else, but got $event"
      }
      check(!isRestored) { "Expected not to be observing lifecycle after restoration." }

      // We don't care about the lifecycle anymore, we've got what we need.
      source.lifecycle.removeObserver(this)

      // These properties are guaranteed to be non-null because this observer is only registered
      // while attached, and these properties are always non-null while attached.
      val restoredState =
        parentRegistryOwner!!.savedStateRegistry.consumeRestoredStateForKey(parentKey!!)
      restoreFromBundle(restoredState)
    }
  }

  /**
   * Must be called when the owning view gets attached to the window. The owning view should find
   * its [SavedStateRegistryOwner] (probably via
   * [androidx.savedstate.ViewTreeSavedStateRegistryOwner]) and determine a string key unique within
   * its parent to save and restore this class in that registry. These values will be cached in this
   * object for [detachment][detachFromParentRegistry] later.
   *
   * This method will register on the parent registry to save any child registries registered with
   * [saveRegistryController].
   *
   * If this object has not been restored yet, this method will start listening to the parent
   * lifecycle to know when to restore.
   *
   * Must be accompanied by a call to [detachFromParentRegistry] when the view is detached.
   */
  fun attachToParentRegistry(
    key: String,
    parentOwner: SavedStateRegistryOwner
  ) {
    // attachToParentRegistry may be called multiple times without a matching detach in some cases,
    // eg. when certain UI tests have failed and are being torn down. Ensure that if that happens
    // we detach from the previous parent first.
    detachFromParentRegistry()

    this.parentRegistryOwner = parentOwner
    this.parentKey = key

    // We can only be restored once.
    if (isRestored) return

    val parentRegistry = parentOwner.savedStateRegistry
    val parentLifecycle = parentOwner.lifecycle

    // If the key is already registered, SavedStateRegistry throws an exception that doesn't provide
    // enough information to troubleshoot, so we add some ourselves.
    try {
      parentRegistry.registerSavedStateProvider(key, ::saveToBundle)
    } catch (e: IllegalArgumentException) {
      throw IllegalArgumentException(
        "Error registering StateRegistryHolder as SavedStateProvider with key \"$key\" on parent " +
          "SavedStateRegistryOwner $parentOwner.\n" +
          "You might need to set a view ID on your BackStackContainer or wrap your " +
          "BackStackScreen with a Named rendering.",
        e
      )
    }

    // Even if the parent lifecycle is in a state further than CREATED, new observers are sent all
    // the lifecycle events required to catch the observer up to the current state, so this handles
    // both the cases where we're ready to immediately restore, and where we have to wait.
    parentLifecycle.addObserver(lifecycleObserver)
  }

  /**
   * Must be called when the owning view detaches from the window.
   *
   * Stops listening to the parent lifecycle and unregisters from the parent registry.
   */
  fun detachFromParentRegistry() {
    // parentKey will only be null if parentRegistryOwner is also null.
    parentRegistryOwner?.savedStateRegistry?.unregisterSavedStateProvider(parentKey!!)
    parentRegistryOwner?.lifecycle?.removeObserver(lifecycleObserver)
    parentRegistryOwner = null
    parentKey = null
  }

  /**
   * Asks [controller] to [save itself][SavedStateRegistryController.performSave] and caches that
   * saved state for when our parent registry gets saved. This should be called any time the owning
   * viewis going away but may be shown again later, and from the [onWillSave] callback.
   *
   * @param key The key used to distinguish [controller] from other controllers saved to this
   * object. Must be the same key used to [restore][restoreRegistryControllerIfReady] the controller
   * later.
   */
  fun saveRegistryController(
    key: String,
    controller: SavedStateRegistryController
  ) {
    doIfRestored { states ->
      val state = Bundle()
      controller.performSave(state)
      states += key to state
    }
  }

  /**
   * If this object has been restored from its parent registry, restored the child [controller]
   * that was previously [saved][saveRegistryController] with the same [key]. This should be called
   * any time the view associated with the child controller is being shown again, and from the
   * [onRestored] callback.
   *
   * This method can only be called once per key per instance of this class. After a key has been
   * restored, its data will be removed from this object.
   *
   * @param key The key used to distinguish [controller] from other controllers saved to this
   * object. Must be the same key used to [save][saveRegistryController] the controller earlier.
   */
  fun restoreRegistryControllerIfReady(
    key: String,
    controller: SavedStateRegistryController
  ) {
    doIfRestored { states ->
      val state = states.remove(key)
      controller.performRestore(state)
    }
  }

  /**
   * Removes all entries from [states] that don't have keys in [retaining].
   */
  fun pruneKeys(retaining: Collection<String>) {
    doIfRestored { states ->
      val deadKeys = states.keys - retaining
      states -= deadKeys
    }
  }

  private inline fun doIfRestored(block: (MutableMap<String, Bundle>) -> Unit) {
    states?.let(block)
  }

  private fun saveToBundle() = Bundle().apply {
    doIfRestored { states ->
      onWillSave(this@StateRegistryAggregator)

      // Convert states map to a bundle.
      states.forEach { (key, state) -> putBundle(key, state) }
    }
  }

  private fun restoreFromBundle(restoredState: Bundle?) {
    check(states == null) { "Expected performRestore to be called only once." }
    states = mutableMapOf()
    restoredState?.keySet()?.forEach { key ->
      states!! += key to restoredState.getBundle(key)!!
    }
    onRestored(this)
  }
}
