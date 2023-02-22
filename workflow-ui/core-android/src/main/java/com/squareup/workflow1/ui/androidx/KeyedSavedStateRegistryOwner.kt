package com.squareup.workflow1.ui.androidx

import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

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
 * @param key The key used to save and restore this controller from a [SavedStateRegistry].
 * @param lifecycleOwner The [LifecycleOwner] that will be delegated to by this instance.
 * (Required because [SavedStateRegistryOwner] extends [LifecycleOwner] for no clear reason.)
 */
@WorkflowUiExperimentalApi
internal class KeyedSavedStateRegistryOwner internal constructor(
  public val key: String,
  lifecycleOwner: LifecycleOwner
) : SavedStateRegistryOwner, LifecycleOwner by lifecycleOwner {
  internal val controller: SavedStateRegistryController = SavedStateRegistryController.create(this)
  override val savedStateRegistry: SavedStateRegistry
    get() = controller.savedStateRegistry
}
