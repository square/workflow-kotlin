package com.squareup.workflow1.presenter

import androidx.compose.runtime.State

/**
 * Resolves [ViewModelRef]s outside of a presenter composition, e.g. for the view layer.
 */
interface ViewModelResolver {
  /**
   * @param resolveRecursively If true then resolves this ref recursively so that the returned value is a
   * concrete [ViewModel] and not another ref.
   */
  fun resolveAsState(
    ref: ViewModelRef,
    resolveRecursively: Boolean = true
  ): State<ViewModel>
}

fun ViewModelResolver.resolve(
  viewModel: ViewModel,
  resolveRefsRecursively: Boolean = true
): ViewModel = (viewModel as? ViewModelRef)
  ?.let { resolveAsState(viewModel, resolveRecursively = resolveRefsRecursively).value }
  ?: viewModel
