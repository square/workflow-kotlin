package com.squareup.workflow1.ui.androidx

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * Namespace for some helper functions for interacting with the AndroidX libraries.
 */
public object WorkflowAndroidXSupport {
  /**
   * Returns the [LifecycleOwner] managing [context].
   *
   * @throws IllegalArgumentException if [context] is unmanaged
   */
  @WorkflowUiExperimentalApi
  public fun lifecycleOwnerFromContext(context: Context): LifecycleOwner =
    requireNotNull(context.ownerOrNull(LifecycleOwner::class)) {
      "Expected $context to lead to a LifecycleOwner"
    }

  /**
   * Tries to get the parent lifecycle from the current view via [findViewTreeLifecycleOwner], if that
   * fails it looks up the context chain for a [LifecycleOwner], and if that fails it just returns
   * null. This differs from [findViewTreeLifecycleOwner] because it will check the
   * [View.getContext] if no owner is found in the view tree.
   */
  @WorkflowUiExperimentalApi
  public fun lifecycleOwnerFromViewTreeOrContextOrNull(view: View): LifecycleOwner? =
    view.findViewTreeLifecycleOwner() ?: view.context.ownerOrNull(LifecycleOwner::class)

  /**
   * Tries to get the parent [SavedStateRegistryOwner] from the current view via
   * [findViewTreeSavedStateRegistryOwner], if that fails it looks up the context chain for a registry
   * owner, and if that fails it just returns null. This differs from
   * [findViewTreeSavedStateRegistryOwner] because it will check the [View.getContext] if no owner
   * is found in the view tree.
   */
  @WorkflowUiExperimentalApi
  public fun stateRegistryOwnerFromViewTreeOrContext(view: View): SavedStateRegistryOwner =
    checkNotNull(stateRegistryOwnerFromViewTreeOrContextOrNull(view)) {
      "Expected to find a SavedStateRegistryOwner either in a parent view or the Context of $view"
    }

  /**
   * Looks for an [OnBackPressedDispatcherOwner] in the receiving [ViewEnvironment].
   * Failing that, falls through to [View.onBackPressedDispatcherOwnerOrNull].
   * Patterned after the heuristic in Compose's `LocalOnBackPressedDispatcherOwner`.
   *
   * Mainly intended as support for finding the [OnBackPressedDispatcherOwner] parameter
   * required by [WorkflowLifecycleOwner.installOn]. That is, this is for
   * use by custom containers that can't use
   * [WorkflowViewStub][com.squareup.workflow1.ui.WorkflowViewStub] or other standard
   * containers, which have this call built in.
   *
   * @throws IllegalArgumentException if no [OnBackPressedDispatcherOwner] can be found
   */
  @WorkflowUiExperimentalApi
  public fun ViewEnvironment.onBackPressedDispatcherOwner(
    container: View
  ): OnBackPressedDispatcherOwner {
    return (map[OnBackPressedDispatcherOwnerKey] as? OnBackPressedDispatcherOwner)
      ?: container.onBackPressedDispatcherOwnerOrNull()
      ?: throw IllegalArgumentException(
        "Expected to find an OnBackPressedDispatcherOwner in one of: $this " +
          "bound to OnBackPressedDispatcherOwnerKey, or " +
          "$container via findViewTreeOnBackPressedDispatcherOwner(), or " +
          "up the Context chain of that view."
      )
  }

  /**
   * Looks for a [View]'s [OnBackPressedDispatcherOwner] via the usual
   * [findViewTreeOnBackPressedDispatcherOwner] method, and if that fails
   * checks its [Context][View.getContext].
   */
  @WorkflowUiExperimentalApi
  public fun View.onBackPressedDispatcherOwnerOrNull(): OnBackPressedDispatcherOwner? {
    return findViewTreeOnBackPressedDispatcherOwner()
      ?: context.ownerOrNull(OnBackPressedDispatcherOwner::class)
  }

  /**
   * Tries to get the parent [SavedStateRegistryOwner] from the current view via
   * [findViewTreeSavedStateRegistryOwner], if that fails it looks up the context chain for a registry
   * owner, and if that fails it just returns null. This differs from
   * [findViewTreeSavedStateRegistryOwner] because it will check the [View.getContext] if no owner
   * is found in the view tree.
   */
  @WorkflowUiExperimentalApi
  private fun stateRegistryOwnerFromViewTreeOrContextOrNull(view: View): SavedStateRegistryOwner? =
    (view.findViewTreeSavedStateRegistryOwner())
      ?: view.context.ownerOrNull(SavedStateRegistryOwner::class)

  private tailrec fun <T : Any> Context.ownerOrNull(ownerClass: KClass<T>): T? =
    when {
      ownerClass.isInstance(this) -> ownerClass.cast(this)
      else -> (this as? ContextWrapper)?.baseContext?.ownerOrNull(ownerClass)
    }
}
