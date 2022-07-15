package com.squareup.workflow1.ui.androidx

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Manages a group of [ViewTreeSavedStateRegistryOwner]s that are all saved to
 * and restored from a single "parent" [SavedStateRegistryOwner]. [SavedStateRegistryOwner]
 * is the new androidx alternative to the [View.onSaveInstanceState] system, and
 * is required by Compose UI.
 *
 * This class is designed to support a navigation container view that owns a
 * a set of navigation "frames", where a frame is something that can be navigated
 * to/from. A frame loosely consists of a root [View] and its
 * [ViewTreeSavedStateRegistryOwner]. For example:
 *
 *  - a back stack container view will own an instance of [WorkflowSavedStateRegistryAggregator],
 *   and use it to assign a [ViewTreeSavedStateRegistryOwner] for its top view.
 *
 *  - a container view managing a set of windows will own an instance of
 *   [WorkflowSavedStateRegistryAggregator], and use it to assign a [ViewTreeSavedStateRegistryOwner]
 *   to each dialog's content view.
 *
 * Note that a [ViewTreeSavedStateRegistryOwner] works _in parallel_ to a
 * [ViewTreeLifecycleOwner][androidx.lifecycle.ViewTreeLifecycleOwner].
 * Use [WorkflowLifecycleOwner] to ensure one is properly installed.
 *
 * [attachToParentRegistry] must be called when the container view is attached to a window,
 * and passed the parent registry. [detachFromParentRegistry] must be called when the
 * container view is detached.
 *
 * Call [installChildRegistryOwnerOn] to put a [ViewTreeSavedStateRegistryOwner]
 * in place on each managed child view, _before it is attached to a window_. After that:
 *
 *  - call [saveAndPruneChildRegistryOwner] if the child is removed from service but may
 *   be restored before [detachFromParentRegistry] is called (as when a back stack
 *   pushes and pops)
 *
 *  - call [pruneAllChildRegistryOwnersExcept] when views are permanently removed from service, taking care
 *   to identify the set that remain active
 *
 * Note that this class _does not_ offer support for the pre-Jetpack [View.onSaveInstanceState]
 * mechanism. Container views must handle that themselves.
 */
@WorkflowUiExperimentalApi
public class WorkflowSavedStateRegistryAggregator {
  /**
   * Holds any states restored from the parent registry, as well as any states saved
   * after that time via [saveAndPruneChildRegistryOwner].
   *
   * Will be null until we are restored from the parent registry. After being restored,
   * it will never be re-assigned again.
   */
  private var states: MutableMap<String, Bundle>? = null

  private val isRestored get() = states != null

  /** Memoize the registry owner passed to [attachToParentRegistry] so it can be detached later. */
  private var parentRegistryOwner: SavedStateRegistryOwner? = null
  private var parentKey: String? = null

  /**
   * The set of [KeyedSavedStateRegistryOwner] instances created by
   * [installChildRegistryOwnerOn], which have not
   * yet been retired via [saveAndPruneChildRegistryOwner] or [pruneAllChildRegistryOwnersExcept].
   */
  private val children = mutableMapOf<String, KeyedSavedStateRegistryOwner>()

  /**
   * Used to observe the parent registry's lifecycle to know when it becomes `CREATED`
   * and is ready for us to restore ourselves. This observer is only registered between
   * calls to [attachToParentRegistry] and [detachFromParentRegistry], and will only
   * be registered if this instance has not already been restored.
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
      restoreFromBundle(
        parentRegistryOwner!!.savedStateRegistry.consumeRestoredStateForKey(parentKey!!)
      )
    }
  }

  /**
   * Must be called when the owning container view gets attached to the window.
   * The owning view should find its [parentOwner] (probably via
   * [WorkflowAndroidXSupport.stateRegistryOwnerFromViewTreeOrContext])
   * and determine a string key unique within
   * that parent to save and restore this class in that registry. These values will be
   * cached in this object for [detachment][detachFromParentRegistry] later.
   *
   * This method will register on the [parentOwner]'s registry to save any child registries
   * created with [installChildRegistryOwnerOn].
   *
   * If this object has not been restored yet, this method will start listening to
   * the [parentOwner]'s lifecycle to know when to restore.
   *
   * Must be accompanied by a call to [detachFromParentRegistry] when the container
   * view is detached.
   *
   * @param key an id for this [WorkflowSavedStateRegistryAggregator], uniquely identifying
   * it in [parentOwner].  Typically this is derived from the
   * [compatibility key][com.squareup.workflow1.ui.Compatible.keyFor] of the rendering
   * of the owning container view.
   */
  public fun attachToParentRegistry(
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
        "Error registering SavedStateProvider: key \"$key\" is already in " +
          "use on parent SavedStateRegistryOwner $parentOwner. " +
          "This is most easily remedied by giving your container Screen rendering a unique " +
          "Compatible.compatibilityKey, perhaps by wrapping it with Named.",
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
  public fun detachFromParentRegistry() {
    // parentKey will only be null if parentRegistryOwner is also null.
    parentRegistryOwner?.savedStateRegistry?.unregisterSavedStateProvider(parentKey!!)
    parentRegistryOwner?.lifecycle?.removeObserver(lifecycleObserver)
    parentRegistryOwner = null
    parentKey = null
  }

  /**
   * Puts a new [ViewTreeSavedStateRegistryOwner] in place on [view], registered
   * with its [ViewTreeLifecycleOwner]. (Use [WorkflowLifecycleOwner] to ensure
   * one is properly installed.)
   *
   * **This method must be called before [view] is attached to a window.**
   *
   * Clean up requirements after making this call are nuanced. There is no need
   * to remove the [SavedStateRegistryOwner] from the [view] itself, but this
   * [WorkflowSavedStateRegistryAggregator] must be informed when [view] is retired from use:
   *
   *  - if [view] is dropped but may later be replaced with a new instance, as when
   *   pushing and popping a back stack, call [saveAndPruneChildRegistryOwner].
   *   This will capture the outgoing view's state, and to stop requesting updates from it.
   *   The saved state will be restored to the next [view] passed to
   *   [installChildRegistryOwnerOn] with the same [key]
   *
   *  - if [view] is dropped and will not be restored, as when a window is closed or
   *    back stack history is modified, call [prune] _with the keys of the views that
   *    remain active_.
   *
   * @param key identifier for the new [ViewTreeSavedStateRegistryOwner], unique across this
   * [WorkflowSavedStateRegistryAggregator]. Typically this is derived from the
   * [compatibility key][com.squareup.workflow1.ui.Compatible.keyFor] of the [view]'s
   * rendering.
   */
  public fun installChildRegistryOwnerOn(
    view: View,
    key: String
  ) {
    val lifecycleOwner = requireNotNull(ViewTreeLifecycleOwner.get(view)) {
      "Expected $view($key) to have a ViewTreeLifecycleOwner. " +
        "Use WorkflowLifecycleOwner to fix that."
    }
    val registryOwner = KeyedSavedStateRegistryOwner(key, lifecycleOwner)
    children.put(key, registryOwner)?.let {
      throw IllegalArgumentException("$key is already in use, it cannot be used to register $view")
    }
    ViewTreeSavedStateRegistryOwner.get(view)?.let {
      throw IllegalArgumentException("$view already has ViewTreeSavedStateRegistryOwner: $it")
    }
    ViewTreeSavedStateRegistryOwner.set(view, registryOwner)
    restoreIfOwnerReady(registryOwner)
  }

  /**
   * Call this when the [View] previously [registered][installChildRegistryOwnerOn]
   * with [key] is being dropped, but may be replaced with a new instance before the container
   * is destroyed -- think of pushing and popping in a back stack.
   *
   * The saved state will be restored if a new [View] is
   * [registered][installChildRegistryOwnerOn] with the same [key].
   */
  public fun saveAndPruneChildRegistryOwner(key: String) {
    children.remove(key)?.let { saveIfOwnerReady(it) }
      ?: throw IllegalArgumentException("No such child: $key")
  }

  private fun saveIfOwnerReady(child: KeyedSavedStateRegistryOwner) {
    doIfRestored { states ->
      val state = Bundle()
      child.controller.performSave(state)
      states += child.key to state
    }
  }

  /**
   * If this object has been restored from its parent registry, restores the child
   * [KeyedSavedStateRegistryOwner.controller] that was previously
   * [saved][saveAndPruneChildRegistryOwner]
   * with the same [KeyedSavedStateRegistryOwner.key].
   */
  private fun restoreIfOwnerReady(
    child: KeyedSavedStateRegistryOwner
  ) {
    doIfRestored { states ->
      val state = states.remove(child.key)
      child.controller.performRestore(state)
    }
  }

  /**
   * Drops all child [ViewTreeSavedStateRegistryOwner]s and their restored
   * state, except those identified in [keysToKeep].
   */
  public fun pruneAllChildRegistryOwnersExcept(keysToKeep: Collection<String> = emptyList()) {
    (children.keys - keysToKeep).forEach { children.remove(it) }

    doIfRestored { states ->
      val deadKeys = states.keys - keysToKeep
      states -= deadKeys
    }
  }

  private inline fun doIfRestored(block: (MutableMap<String, Bundle>) -> Unit) {
    states?.let(block)
  }

  private fun saveToBundle() = Bundle().apply {
    doIfRestored { states ->
      children.values.forEach { saveIfOwnerReady(it) }
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
    children.values.forEach {
      // We're only allowed to restore from an INITIALIZED state, but this callback can also be
      // invoked while the owner is already CREATED, at least on API 32.
      // https://github.com/square/workflow-kotlin/issues/570
      if (it.lifecycle.currentState == INITIALIZED) restoreIfOwnerReady(it)
    }
  }
}
