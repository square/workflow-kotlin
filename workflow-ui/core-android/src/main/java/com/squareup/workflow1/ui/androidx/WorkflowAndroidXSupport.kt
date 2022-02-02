package com.squareup.workflow1.ui.androidx

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.getRendering
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * Namespace for some helper functions for interacting with the AndroidX libraries.
 */
public object WorkflowAndroidXSupport {

  /**
   * Tries to get the parent lifecycle from the current view via [ViewTreeLifecycleOwner], if that
   * fails it looks up the context chain for a [LifecycleOwner], and if that fails it just returns
   * null. This differs from [ViewTreeLifecycleOwner.get] because it will check the
   * [View.getContext] if no owner is found in the view tree.
   */
  @WorkflowUiExperimentalApi
  public fun lifecycleOwnerFromViewTreeOrContext(view: View): LifecycleOwner? =
    ViewTreeLifecycleOwner.get(view) ?: view.context.ownerOrNull(LifecycleOwner::class)

  /**
   * Tries to get the parent [SavedStateRegistryOwner] from the current view via
   * [ViewTreeSavedStateRegistryOwner], if that fails it looks up the context chain for a registry
   * owner, and if that fails it just returns null. This differs from
   * [ViewTreeSavedStateRegistryOwner.get] because it will check the [View.getContext] if no owner
   * is found in the view tree.
   */
  @WorkflowUiExperimentalApi
  public fun requireStateRegistryOwnerFromViewTreeOrContext(view: View): SavedStateRegistryOwner =
    checkNotNull(stateRegistryOwnerFromViewTreeOrContext(view)) {
      "Expected to find a SavedStateRegistryOwner either in a parent view or the Context of this " +
        javaClass.name
    }

  /**
   * Tries to get the parent [SavedStateRegistryOwner] from the current view via
   * [ViewTreeSavedStateRegistryOwner], if that fails it looks up the context chain for a registry
   * owner, and if that fails it just returns null. This differs from
   * [ViewTreeSavedStateRegistryOwner.get] because it will check the [View.getContext] if no owner
   * is found in the view tree.
   */
  @WorkflowUiExperimentalApi
  private fun stateRegistryOwnerFromViewTreeOrContext(view: View): SavedStateRegistryOwner? =
    ViewTreeSavedStateRegistryOwner.get(view)
      ?: view.context.ownerOrNull(SavedStateRegistryOwner::class)

  /**
   * Attempts to generate a unique key for the given [containerView] for registering the view on
   * a [SavedStateRegistry].
   *
   * The [SavedStateRegistry] API involves defining string keys to associate with state bundles.
   * These keys must be unique relative to the instance of the registry they are saved in. This
   * function tries to satisfy that requirement by combining [containerView]'s fully-qualified class
   * name with both its [view ID][View.getId] and the
   * [compatibility key][com.squareup.workflow1.ui.Compatible.compatibilityKey] of its rendering.
   * This method isn't guaranteed to give a unique key, but it should be good enough: If you need to
   * nest multiple containers of the same type under the same [SavedStateRegistry], just wrap each
   * container's rendering with a [Named] or give each container view a unique view ID.
   *
   * There's a potential issue here where if [containerView]'s ID is changed to something else, then
   * another container is added with the old ID, the new container will overwrite the old one's
   * state. Since they'd both be using the same key, [SavedStateRegistry] would throw an exception.
   * However, as long as the first container is detached before its ID is changed this shouldn't be
   * a problem.
   */
  @OptIn(WorkflowUiExperimentalApi::class)
  public fun createStateRegistryKeyForContainer(containerView: View): String {
    val nameSuffix = containerView.getRendering<Any>()
      ?.let { Compatible.keyFor(it) }?.let { "-$it" }
      ?: ""
    val idSuffix = if (containerView.id == FrameLayout.NO_ID) "" else "-${containerView.id}"
    return containerView.javaClass.name + nameSuffix + idSuffix
  }

  private tailrec fun <T : Any> Context.ownerOrNull(ownerClass: KClass<T>): T? =
    when {
      ownerClass.isInstance(this) -> ownerClass.cast(this)
      else -> (this as? ContextWrapper)?.baseContext?.ownerOrNull(ownerClass)
    }
}
