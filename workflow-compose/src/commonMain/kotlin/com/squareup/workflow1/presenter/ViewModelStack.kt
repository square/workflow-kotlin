package com.squareup.workflow1.presenter

/**
 * A [ViewModel] that displays a list of [ViewModel]s or [ViewModelRef]s as a layered stack.
 *
 * This is returned from [simplify] when the list contains more than one item.
 */
data class ViewModelStack(
  val viewModels: List<ViewModel>
) : ViewModel
