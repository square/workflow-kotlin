package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.ViewEnvironmentKey

/**
 * The default [OverlayDialogFactory] for [AlertOverlay] reads this value
 * for the `@StyleRes themeResId: Int` argument of `AlertDialog.Builder()`.
 */
public object AlertDialogThemeResId : ViewEnvironmentKey<Int>() {
  override val default: Int = 0
}
