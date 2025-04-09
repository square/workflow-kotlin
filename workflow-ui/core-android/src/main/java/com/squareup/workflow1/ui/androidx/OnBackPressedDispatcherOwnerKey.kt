package com.squareup.workflow1.ui.androidx

import androidx.activity.OnBackPressedDispatcherOwner
import com.squareup.workflow1.ui.ViewEnvironmentKey

/**
 * Used by container classes to ensure that
 * [View.findViewTreeOnBackPressedDispatcherOwner][androidx.activity.findViewTreeOnBackPressedDispatcherOwner]
 * works before new views are attached to their parents. Not intended for use by
 * feature code.
 */
public object OnBackPressedDispatcherOwnerKey :
  ViewEnvironmentKey<OnBackPressedDispatcherOwner>() {
  override val default: OnBackPressedDispatcherOwner get() = error("Unset")
}
