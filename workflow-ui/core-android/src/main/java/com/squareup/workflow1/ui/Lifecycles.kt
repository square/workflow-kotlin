package com.squareup.workflow1.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * The [Lifecycle] for this context, or null if one can't be found.
 */
@WorkflowUiExperimentalApi
public tailrec fun Context.lifecycleOrNull(): Lifecycle? = when (this) {
  is LifecycleOwner -> this.lifecycle
  else -> (this as? ContextWrapper)?.baseContext?.lifecycleOrNull()
}
