package com.squareup.workflow1.ui.androidx

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
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
   * Tries to get the parent lifecycle from the current view via [ViewTreeLifecycleOwner], if that
   * fails it looks up the context chain for a [LifecycleOwner], and if that fails it just returns
   * null. This differs from [ViewTreeLifecycleOwner.get] because it will check the
   * [View.getContext] if no owner is found in the view tree.
   */
  @WorkflowUiExperimentalApi
  public fun lifecycleOwnerFromViewTreeOrContextOrNull(view: View): LifecycleOwner? =
    ViewTreeLifecycleOwner.get(view) ?: view.context.ownerOrNull(LifecycleOwner::class)

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
