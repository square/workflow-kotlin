package com.squareup.workflow1.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

@WorkflowUiExperimentalApi
public typealias ViewBindingInflater<BindingT> = (LayoutInflater, ViewGroup?, Boolean) -> BindingT

/** Function that updates the UI built by a [ScreenViewFactory]. */
@WorkflowUiExperimentalApi
public fun interface ScreenViewUpdater<ScreenT : Screen> {
  public fun showRendering(
    rendering: ScreenT,
    viewEnvironment: ViewEnvironment
  )
}

internal fun Context.viewBindingLayoutInflater(container: ViewGroup?) =
  LayoutInflater.from(container?.context ?: this)
      .cloneInContext(this)
