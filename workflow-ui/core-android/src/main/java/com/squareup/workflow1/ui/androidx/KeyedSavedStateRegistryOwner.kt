package com.squareup.workflow1.ui.androidx

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner

/**
 * The implementation of [SavedStateRegistryOwner] that should be installed on every immediate
 * child view of container root views (e.g. content views, e.g. backstack frames) so that when
 * a view inside a container calls [findViewTreeSavedStateRegistryOwner] on itself, one of these
 * is returned.
 *
 * The container should use a [WorkflowSavedStateRegistryAggregator] to manage its set of
 * [KeyedSavedStateRegistryOwner] instances, which will save and restore them
 * via its own [SavedStateRegistryOwner].
 *
 * To create an instance, call [WorkflowSavedStateRegistryAggregator.installChildRegistryOwnerOn].
 *
 * ## Lifecycle
 *
 * This owner exposes its own [Lifecycle] rather than simply delegating to [lifecycleOwner]'s
 * (typically the [WorkflowLifecycleOwner] of the view it is installed on). The local lifecycle
 * mirrors [lifecycleOwner]'s, with one crucial difference: it is held at
 * [INITIALIZED][Lifecycle.State.INITIALIZED] until this owner's [savedStateRegistry] has been
 * restored, and only then advances.
 *
 * This enforces the androidx savedstate contract that a [SavedStateRegistry] is always restored
 * before its owner's lifecycle moves to [CREATED][Lifecycle.State.CREATED]:
 *
 *  - [SavedStateRegistryController.performRestore] throws if the owner's lifecycle is at least
 *    `STARTED`, so restoration stays legal no matter how far [lifecycleOwner]'s lifecycle has
 *    advanced by the time the aggregator's state arrives.
 *  - Consumers such as Compose UI's `DisposableSaveableStateRegistry` call
 *    [SavedStateRegistry.consumeRestoredStateForKey], which throws if the registry has not been
 *    restored. They are driven by lifecycle signals, so refusing to advance until restoration
 *    makes it impossible for them to observe an unrestored registry.
 *
 * [WorkflowLifecycleOwner] lifecycles advance in a synchronous, depth-first cascade when a view
 * tree attaches to a window. Observer dispatch order on those lifecycles depends on registration
 * order, so a descendant's observers (e.g. a nested `ComposeView`'s composition) can run before
 * the aggregator's own restoration observer ever fires. To close that race, when [lifecycleOwner]
 * delivers `ON_CREATE` and this owner has not been restored yet, it synchronously asks the
 * aggregator to restore it *now* via [onRestoreNeeded] — before the local lifecycle advances and
 * any downstream consumer can see the registry. [installObserver] is called by the aggregator
 * before the view can possibly attach, so this observer is always registered — and thus always
 * dispatched — ahead of any consumer's.
 *
 * @param key The key used to save and restore this controller from a [SavedStateRegistry].
 * @param lifecycleOwner The [LifecycleOwner] this owner's lifecycle follows. (Required because
 * [SavedStateRegistryOwner] extends [LifecycleOwner] for no clear reason.)
 * @param onRestoreNeeded Called at most once, when [lifecycleOwner]'s lifecycle is leaving
 * `INITIALIZED` but this owner's registry has not been restored yet. The callee must restore
 * [controller] (possibly with a null state) before returning.
 */
internal class KeyedSavedStateRegistryOwner internal constructor(
  val key: String,
  private val lifecycleOwner: LifecycleOwner,
  private val onRestoreNeeded: (KeyedSavedStateRegistryOwner) -> Unit
) : SavedStateRegistryOwner {
  private val localLifecycle = LifecycleRegistry(this)

  override val lifecycle: Lifecycle get() = localLifecycle

  internal val controller: SavedStateRegistryController = SavedStateRegistryController.create(this)
  override val savedStateRegistry: SavedStateRegistry
    get() = controller.savedStateRegistry

  private val lifecycleObserver = object : LifecycleEventObserver {
    override fun onStateChanged(
      source: LifecycleOwner,
      event: Lifecycle.Event
    ) {
      // The delegate lifecycle is leaving INITIALIZED, so consumers keyed on our lifecycle are
      // about to be able to read our registry. Restoration must happen first, and it is still
      // legal because localLifecycle is INITIALIZED until we advance it below.
      if (event == ON_CREATE && !savedStateRegistry.isRestored) {
        onRestoreNeeded(this@KeyedSavedStateRegistryOwner)
        check(savedStateRegistry.isRestored) {
          "onRestoreNeeded contract violation: registry for key '$key' was not restored"
        }
      }
      localLifecycle.handleLifecycleEvent(event)
      if (event == ON_DESTROY) {
        source.lifecycle.removeObserver(this)
      }
    }
  }

  /**
   * Starts mirroring [lifecycleOwner]'s lifecycle. Must be called after the aggregator has
   * registered this owner, and before the view this owner is installed on can attach to a window.
   */
  internal fun installObserver() {
    // A DESTROYED lifecycle will never advance, so nothing can ever legally consume this
    // registry through lifecycle signals; observing would just throw.
    if (lifecycleOwner.lifecycle.currentState == DESTROYED) return
    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
  }

  override fun toString(): String {
    return "KeyedSavedStateRegistryOwner(key='$key', controller=$controller)"
  }
}
