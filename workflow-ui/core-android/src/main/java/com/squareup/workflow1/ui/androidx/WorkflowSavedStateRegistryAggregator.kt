package com.squareup.workflow1.ui.androidx

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.squareup.workflow1.internal.requireNotNullWithKey
import com.squareup.workflow1.internal.withKey

/**
 * Manages a group of [SavedStateRegistryOwner]s that are all saved to and restored from a single
 * "parent" [SavedStateRegistryOwner]. [SavedStateRegistryOwner] is the new androidx alternative to
 * the [View.onSaveInstanceState] system, and is required by Compose UI.
 *
 * This class is designed to support a navigation container view that owns a a set of navigation
 * "frames", where a frame is something that can be navigated to/from. A frame loosely consists of a
 * root [View] and its [SavedStateRegistryOwner]. For example:
 *
 * - a back stack container view will own an instance of [WorkflowSavedStateRegistryAggregator], and
 *   use it to assign a [SavedStateRegistryOwner] for its top view.
 *
 * - a container view managing a set of windows will own an instance of
 *   [WorkflowSavedStateRegistryAggregator], and use it to assign a [SavedStateRegistryOwner] to
 *   each dialog's content view.
 *
 * Note that a [SavedStateRegistryOwner] works _in parallel_ to a
 * [LifecycleOwner][androidx.lifecycle.LifecycleOwner]. Use [WorkflowLifecycleOwner] to ensure one
 * is properly installed.
 *
 * [attachToParentRegistry] must be called when the container view is attached to a window, and
 * passed the parent registry. [detachFromParentRegistry] must be called when the container view is
 * detached.
 *
 * Call [installChildRegistryOwnerOn] to put a [SavedStateRegistryOwner] in place on each managed
 * child view, _before it is attached to a window_. After that:
 *
 * - call [saveAndPruneChildRegistryOwner] if the child is removed from service but may be restored
 *   before [detachFromParentRegistry] is called (as when a back stack pushes and pops)
 *
 * - call [pruneAllChildRegistryOwnersExcept] when views are permanently removed from service,
 *   taking care to identify the set that remain active
 *
 * Note that this class _does not_ offer support for the pre-Jetpack [View.onSaveInstanceState]
 * mechanism. Container views must handle that themselves.
 */
public class WorkflowSavedStateRegistryAggregator {
  /**
   * Holds any states restored from the parent registry, as well as any states saved after that time
   * via [saveAndPruneChildRegistryOwner].
   *
   * Will be null until we are restored from the parent registry. After being restored, it will
   * never be re-assigned again.
   */
  private var states: MutableMap<String, Bundle>? = null

  private val isRestored
    get() = states != null

  /** Memoize the registry owner passed to [attachToParentRegistry] so it can be detached later. */
  private var parentRegistryOwner: SavedStateRegistryOwner? = null
  private var parentKey: String? = null

  /**
   * The set of [KeyedSavedStateRegistryOwner] instances created by [installChildRegistryOwnerOn],
   * which have not yet been retired via [saveAndPruneChildRegistryOwner] or
   * [pruneAllChildRegistryOwnersExcept].
   */
  private val children = mutableMapOf<String, KeyedSavedStateRegistryOwner>()

  /**
   * Used to observe the parent registry's lifecycle to know when it becomes `CREATED` and is ready
   * for us to restore ourselves. This observer is only registered between calls to
   * [attachToParentRegistry] and [detachFromParentRegistry], and will only be registered if this
   * instance has not already been restored.
   */
  private val lifecycleObserver =
    object : LifecycleEventObserver {
      override fun onStateChanged(source: LifecycleOwner, event: Event) {
        // We should always get all the events required to bring this observer from it's initial
        // state
        // (INITIALIZED) up to the current state, as per the contract of Lifecycle. But double-check
        // here just in case we're dealing with a bad implementation, so that this state machine
        // doesn't hang forever in a bad state.
        check(event == ON_CREATE) {
          "Expected to receive ON_CREATE event before anything else, but got $event"
        }
        check(!isRestored) { "Expected not to be observing lifecycle after restoration." }

        // We don't care about the lifecycle anymore, we've got what we need.
        source.lifecycle.removeObserver(this)

        restoreFromBundle(consumeFromParent())
      }
    }

  /**
   * Consumes this aggregator's state from the attached parent registry. Only legal while attached
   * and once the parent registry itself has been restored.
   */
  private fun consumeFromParent(): Bundle? {
    return try {
      // These properties are guaranteed to be non-null because this method is only called
      // while attached, and these properties are always non-null while attached.
      parentRegistryOwner!!.savedStateRegistry.consumeRestoredStateForKey(parentKey!!)
    } catch (e: IllegalStateException) {
      // Exception thrown by SavedStateRegistryOwner is pretty useless.
      throw IllegalStateException("Error consuming $parentKey from $parentRegistryOwner", e)
        .withKey(parentKey.orEmpty())
    }
  }

  /**
   * Must be called when the owning container view gets attached to the window. The owning view
   * should find its [parentOwner] (probably via
   * [WorkflowAndroidXSupport.stateRegistryOwnerFromViewTreeOrContext]) and determine a string key
   * unique within that parent to save and restore this class in that registry. These values will be
   * cached in this object for [detachment][detachFromParentRegistry] later.
   *
   * This method will register on the [parentOwner]'s registry to save any child registries created
   * with [installChildRegistryOwnerOn].
   *
   * If this object has not been restored yet, this method will start listening to the
   * [parentOwner]'s lifecycle to know when to restore.
   *
   * Must be accompanied by a call to [detachFromParentRegistry] when the container view is
   * detached.
   *
   * @param key an id for this [WorkflowSavedStateRegistryAggregator], uniquely identifying it in
   *   [parentOwner]. Typically this is derived from the
   *   [compatibility key][com.squareup.workflow1.ui.Compatible.keyFor] of the rendering of the
   *   owning container view.
   */
  public fun attachToParentRegistry(key: String, parentOwner: SavedStateRegistryOwner) {
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
            "Compatible.compatibilityKey -- note the name fields on BodyAndOverlaysScreen " +
            "and BackStackScreen.",
          e,
        )
        .withKey(key)
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
   * Puts a new [SavedStateRegistryOwner] in place on [view], registered with its [LifecycleOwner].
   * (Use [WorkflowLifecycleOwner] to ensure one is properly installed.)
   *
   * **This method must be called on the main thread, before [view] is attached to a window.** (It
   * observes [view]'s `ViewTreeLifecycleOwner` lifecycle, and
   * [androidx.lifecycle.LifecycleRegistry] requires observer registration on the main thread.)
   *
   * Clean up requirements after making this call are nuanced. There is no need to remove the
   * [SavedStateRegistryOwner] from the [view] itself, but this
   * [WorkflowSavedStateRegistryAggregator] must be informed when [view] is retired from use:
   *
   * - if [view] is dropped but may later be replaced with a new instance, as when pushing and
   *   popping a back stack, call [saveAndPruneChildRegistryOwner]. This will capture the outgoing
   *   view's state, and stop requesting updates from it. The saved state will be restored to the
   *   next [view] passed to [installChildRegistryOwnerOn] with the same [key]
   *
   * - if [view] is dropped and will not be restored, as when a window is closed or back stack
   *   history is modified, call [pruneAllChildRegistryOwnersExcept] _with the keys of the views
   *   that remain active_.
   *
   * @param key identifier for the new [SavedStateRegistryOwner], unique across this
   *   [WorkflowSavedStateRegistryAggregator]. Typically this is derived from the
   *   [compatibility key][com.squareup.workflow1.ui.Compatible.keyFor] of the [view]'s rendering.
   * @param force when this is true we're asserting that it's okay to clobber an existing registry
   *   that may have been put in place by Android. If we find one of our own
   *   [KeyedSavedStateRegistryOwner], we will still throw an exception.
   * @throws IllegalArgumentException is [key] is already in use or if [view] has an unexpected
   *   [SavedStateRegistryOwner] in place already.
   */
  public fun installChildRegistryOwnerOn(view: View, key: String, force: Boolean = false) {
    val lifecycleOwner =
      requireNotNullWithKey(view.findViewTreeLifecycleOwner(), key) {
        "Expected $view($key) to have a ViewTreeLifecycleOwner. " +
          "Use WorkflowLifecycleOwner to fix that."
      }
    val registryOwner = KeyedSavedStateRegistryOwner(key, lifecycleOwner, ::restoreChildNow)
    children.put(key, registryOwner)?.let {
      throw IllegalArgumentException("$key is already in use, it cannot be used to register $view")
        .withKey(key)
    }
    view
      .findViewTreeSavedStateRegistryOwner()
      ?.takeIf { !force || it is KeyedSavedStateRegistryOwner }
      ?.let {
        throw IllegalArgumentException(
            "Using $key to register $view, but it already has SavedStateRegistryOwner: $it"
          )
          .withKey(key)
      }
    view.setViewTreeSavedStateRegistryOwner(registryOwner)
    // Registered only after the child is in [children], so that if the lifecycle is already
    // past INITIALIZED the resulting synchronous restoreChildNow call finds the child.
    registryOwner.installObserver()
    restoreIfOwnerReady(registryOwner)
  }

  /**
   * Called by a [child] whose lifecycle is about to leave `INITIALIZED` while its registry has not
   * been restored yet. Restoration must happen now: as soon as the child's lifecycle moves,
   * lifecycle-driven consumers (e.g. Compose UI's `DisposableSaveableStateRegistry`, created the
   * moment a `ComposeView` under [child]'s view composes) will read the child's registry, and
   * androidx throws if it is unrestored.
   *
   * This situation arises because [WorkflowLifecycleOwner] lifecycles advance in a synchronous
   * depth-first cascade during window-attach, and lifecycle observer dispatch order follows
   * registration order: a descendant's consumers can run before this aggregator's own
   * [lifecycleObserver] receives the parent `ON_CREATE` that would normally have restored
   * everything first (see https://github.com/square/workflow-kotlin/issues/570 and UISA-95).
   *
   * If this aggregator hasn't been restored yet, it first tries to restore itself synchronously
   * from the parent registry. That is legal whenever the parent registry itself has been restored —
   * [androidx.savedstate.SavedStateRegistry.consumeRestoredStateForKey] only requires the parent's
   * *registry* to be restored, not its lifecycle to be `CREATED`. The parent is always restored by
   * this point in practice: lifecycles advance strictly top-down, so every ancestor's lifecycle
   * reached `CREATED` before [child]'s did, which (inductively, via this same mechanism one level
   * up) restored every ancestor registry on the way down.
   */
  private fun restoreChildNow(child: KeyedSavedStateRegistryOwner) {
    if (!isRestored && parentRegistryOwner?.savedStateRegistry?.isRestored == true) {
      // Our own ON_CREATE observer hasn't fired yet, but the parent registry already has our
      // state. Restore ourselves now instead of waiting out the rest of the dispatch cascade.
      parentRegistryOwner?.lifecycle?.removeObserver(lifecycleObserver)
      restoreFromBundle(consumeFromParent())
    }

    // restoreFromBundle above restores every unrestored child, including this one.
    if (child.savedStateRegistry.isRestored) return

    val states = states
    if (states != null && children[child.key] === child) {
      child.controller.performRestore(states.remove(child.key))
    } else {
      // Either this aggregator has no restored state to draw from (its own restoration is
      // still pending), or the child has already been pruned. There is no state available for
      // this child, and its lifecycle is advancing now, so the only way to uphold the
      // savedstate contract is to restore it empty. Note that consuming from [states] here
      // would be wrong for a pruned child: its saved state must remain available for the
      // replacement view that will be installed under the same key.
      val reason =
        if (states == null) {
          "this aggregator (parent key '$parentKey') has not been restored yet"
        } else {
          "the child is no longer registered with this aggregator (already pruned?)"
        }
      Log.d(
        "Workflow",
        "WorkflowSavedStateRegistryAggregator restoring child '${child.key}' with no state " +
          "because $reason. Any state saved under this key is not available.",
      )
      child.controller.performRestore(null)
    }
  }

  /**
   * Call this when the [View] previously [registered][installChildRegistryOwnerOn] with [key] is
   * being dropped, but may be replaced with a new instance before the container is destroyed --
   * think of pushing and popping in a back stack.
   *
   * The saved state will be restored if a new [View] is [registered][installChildRegistryOwnerOn]
   * with the same [key].
   */
  public fun saveAndPruneChildRegistryOwner(key: String) {
    children.remove(key)?.let { saveIfOwnerReady(it) }
      ?: throw IllegalArgumentException("No such child: $key, on parent $parentKey").withKey(key)
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
   * [saved][saveAndPruneChildRegistryOwner] with the same [KeyedSavedStateRegistryOwner.key].
   */
  private fun restoreIfOwnerReady(child: KeyedSavedStateRegistryOwner) {
    // The child may already have been restored by restoreChildNow, if its lifecycle was
    // already advancing when it was installed.
    if (child.savedStateRegistry.isRestored) return

    doIfRestored { states ->
      val state = states.remove(child.key)
      child.controller.performRestore(state)
    }
  }

  /**
   * Drops all child [SavedStateRegistryOwner]s and their restored state, except those identified in
   * [keysToKeep].
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

  private fun saveToBundle() =
    Bundle().apply {
      doIfRestored { states ->
        children.values.forEach { saveIfOwnerReady(it) }
        // Convert states map to a bundle.
        states.forEach { (key, state) -> putBundle(key, state) }
      }
    }

  private fun restoreFromBundle(restoredState: Bundle?) {
    check(states == null) { "Expected performRestore to be called only once." }
    states = mutableMapOf()
    restoredState?.keySet()?.forEach { key -> states!! += key to restoredState.getBundle(key)!! }
    // Any child whose lifecycle already advanced was restored at that moment by
    // restoreChildNow; restoreIfOwnerReady skips those. Everything else is restored here.
    // See https://github.com/square/workflow-kotlin/issues/570.
    children.values.forEach { restoreIfOwnerReady(it) }
  }
}
