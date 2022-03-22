package com.squareup.workflow1.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

@WorkflowUiExperimentalApi
public typealias ViewBindingInflater<BindingT> = (LayoutInflater, ViewGroup?, Boolean) -> BindingT

@WorkflowUiExperimentalApi
public typealias ViewAndRunnerBuilder<T> =
  (ViewEnvironment, Context, ViewGroup?) -> Pair<View, ScreenViewRunner<T>>

/**
 * An object that manages a [View] instance built by a [ScreenViewFactory.buildView], providing
 * continuity between calls to that method and [ScreenViewFactory.updateView]. A [ScreenViewRunner]
 * is instantiated when its [View] is built -- there is a 1:1 relationship between a [View]
 * and the [ScreenViewRunner] that drives it.
 *
 * Note that use of [ScreenViewRunner] is not required by [ScreenViewFactory]. [ScreenViewRunner]
 * is just a convenient bit of glue for working with [AndroidX ViewBinding][ViewBinding], XML
 * layout resources, etc.
 */
@WorkflowUiExperimentalApi
public fun interface ScreenViewRunner<RenderingT : Screen> {
  public fun showRendering(
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  )
}

internal fun Context.viewBindingLayoutInflater(container: ViewGroup?) =
  LayoutInflater.from(container?.context ?: this)
    .cloneInContext(this)
