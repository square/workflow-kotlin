package com.squareup.workflow1.ui

import android.view.View

/**
 * TODO kdoc
 */
public fun interface ViewInitializer {
  /**
   * TODO kdoc
   */
  public fun onViewCreated(view: View)
}

/**
 * Returns a [ViewEnvironment] that includes [viewInitializer] if not null, and which can be read
 * by [extractViewInitializer].
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal fun ViewEnvironment.withViewInitializer(
  viewInitializer: ViewInitializer?
): ViewEnvironment = viewInitializer?.let { this + (ViewInitializerKey to it) } ?: this

/**
 * If this [ViewEnvironment] contains a [ViewInitializer] as written by [withViewInitializer],
 * returns it and the [ViewEnvironment] _without_ the initializer, else returns null and this
 * [ViewEnvironment].
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal fun ViewEnvironment.extractViewInitializer(): Pair<ViewInitializer?, ViewEnvironment> =
  this[ViewInitializerKey].takeUnless { it === NoopViewInitializer }
    ?.let { viewInitializer ->
      Pair(viewInitializer, this + (ViewInitializerKey to NoopViewInitializer))
    } ?: Pair(null, this)

private object NoopViewInitializer : ViewInitializer {
  override fun onViewCreated(view: View) = Unit
}

@OptIn(WorkflowUiExperimentalApi::class)
private object ViewInitializerKey : ViewEnvironmentKey<ViewInitializer>(ViewInitializer::class) {
  override val default: ViewInitializer get() = NoopViewInitializer
}
