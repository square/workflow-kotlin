package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * The default [OverlayDialogFactory] for [AlertOverlay] reads this value
 * for the `@StyleRes themeResId: Int` argument of `AlertDialog.Builder()`.
 */
@WorkflowUiExperimentalApi
public object AlertDialogThemeResId : ViewEnvironmentKey<Int>() {
  override val default: Int = 0
}
