package com.squareup.workflow1.ui

import android.app.Activity
import android.view.View

/**
 * Returns the [WorkflowLayout] serving as the [contentView][Activity.setContentView]
 * of the receiving [Activity], creating it (and replacing the existing view) if
 * necessary.
 */
val Activity.workflowContentView: WorkflowLayout
  get() {
    return workflowContentViewOrNull ?: WorkflowLayout(this).also {
      it.id = R.id.workflow_content_view
      setContentView(it)
    }
  }

/**
 * Returns the [WorkflowLayout] serving as the [contentView][Activity.setContentView]
 * of the receiving [Activity], or null if there isn't one.
 */
val Activity.workflowContentViewOrNull: WorkflowLayout?
  get() = (findViewById<View>(R.id.workflow_content_view) as? WorkflowLayout)
