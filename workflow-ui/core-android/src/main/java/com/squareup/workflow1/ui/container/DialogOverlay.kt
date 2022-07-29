package com.squareup.workflow1.ui.container

import android.app.Dialog
import android.view.View
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Returns the most recent [Overlay] rendering [shown][OverlayDialogHolder.show] in this [Dialog],
 * or throws a [NullPointerException] if the receiver was not created via
 * [OverlayDialogFactory.buildDialog].
 */
@WorkflowUiExperimentalApi
public var Dialog.overlay: Overlay
  get() = checkNotNull(overlayOrNull) {
    "Expected to find an Overlay in tag R.id.workflow_overlay on the decor view of $this"
  }
  internal set(value) = decorView.setTag(R.id.workflow_overlay, value)

/**
 * Returns the most recent [Overlay] rendering [shown][OverlayDialogHolder.show] in this [Dialog],
 * or `null` if the receiver was not created via [OverlayDialogFactory.buildDialog].
 */
@WorkflowUiExperimentalApi
public val Dialog.overlayOrNull: Overlay?
  get() = decorViewOrNull?.getTag(R.id.workflow_overlay) as? Overlay

internal val Dialog.decorView: View
  get() {
    val window = checkNotNull(window) { "Expected to find a window on $this" }
    return window.decorView
  }

internal val Dialog.decorViewOrNull: View?
  get() = window?.peekDecorView()
