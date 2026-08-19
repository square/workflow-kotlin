package com.squareup.workflow1.presenter

fun interface ViewModelProducer<out T : ViewModel> {
  fun ViewModelProducerScope.produce(children: List<ViewModelRef>): T
}

interface ViewModelProducerScope {
  /**
   * Returns the current value of this ref. This creates a hard dependency from the calling producer
   * on this ref's producer, causing the parent to invalidate whenever the child does. This should
   * be used with caution.
   *
   * @param flatten If true then resolves this ref recursively so that the returned value is a
   * concrete [ViewModel] and not another ref.
   */
  fun ViewModelRef.resolve(flatten: Boolean = true): ViewModel
}

@Suppress("UNCHECKED_CAST")
context(ctx: ViewModelProducerScope)
fun <T : ViewModel> ViewModelRef.resolveTyped(flatten: Boolean = true): T = with(ctx) {
  resolve(flatten) as T
}
