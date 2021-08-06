package com.squareup.workflow1.ui

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner

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
    ViewTreeLifecycleOwner.get(view) ?: view.context.lifecycleOwnerOrNull()

  private tailrec fun Context.lifecycleOwnerOrNull(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    else -> (this as? ContextWrapper)?.baseContext?.lifecycleOwnerOrNull()
  }
}
