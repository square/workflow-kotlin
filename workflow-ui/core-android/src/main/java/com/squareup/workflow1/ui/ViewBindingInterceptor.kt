package com.squareup.workflow1.ui

import android.view.View

/**
 * TODO write documentation
 */
public fun interface ViewBindingInterceptor {
  /**
   * TODO kdoc
   *
   * @param proceed This does not accept a view, because the ultimate binding logic expects the view
   * instance it created, so the original view is always passed down. However, the view may be
   * wrapped on the way back up.
   */
  @WorkflowUiExperimentalApi
  public fun initializeView(
    view: View,
    initialRendering: Any,
    initialViewEnvironment: ViewEnvironment,
    proceed: (ViewEnvironment) -> Unit
  )
}

/**
 * Returns a [ViewEnvironment] that will intercept all [View.bindShowRendering] calls with the
 * given [ViewBindingInterceptor].
 */
@WorkflowUiExperimentalApi
public fun ViewEnvironment.withViewBindingInterceptor(
  interceptor: ViewBindingInterceptor
): ViewEnvironment {
  val interceptorList = get(ViewBindingInterceptorList)
  val newInterceptorList = interceptorList + interceptor
  return this + (ViewBindingInterceptorList to newInterceptorList)
}

@WorkflowUiExperimentalApi
public fun ViewEnvironment.withoutViewBindingInterceptor(
  interceptor: ViewBindingInterceptor
): ViewEnvironment {
  val interceptorList = get(ViewBindingInterceptorList)
  val newInterceptorList = interceptorList - interceptor
  return this + (ViewBindingInterceptorList to newInterceptorList)
}

internal class ViewBindingInterceptorList(
  private val interceptors: List<ViewBindingInterceptor>
) {

  operator fun plus(interceptor: ViewBindingInterceptor) =
    ViewBindingInterceptorList(interceptors + interceptor)

  operator fun minus(interceptor: ViewBindingInterceptor) =
    ViewBindingInterceptorList(interceptors - interceptor)

  @OptIn(WorkflowUiExperimentalApi::class)
  fun intercept(
    view: View,
    initialRendering: Any,
    initialViewEnvironment: ViewEnvironment,
    block: (ViewEnvironment) -> Unit
  ) {
    interceptors.foldRight(block) { interceptor, proceed ->
      { newEnv -> interceptor.initializeView(view, initialRendering, newEnv, proceed) }
    }.invoke(initialViewEnvironment)
  }

  @OptIn(WorkflowUiExperimentalApi::class)
  companion object Key : ViewEnvironmentKey<ViewBindingInterceptorList>(
      ViewBindingInterceptorList::class
  ) {
    override val default: ViewBindingInterceptorList = ViewBindingInterceptorList(emptyList())
  }
}
