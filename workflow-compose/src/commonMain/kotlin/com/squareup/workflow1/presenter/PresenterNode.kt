package com.squareup.workflow1.presenter

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlin.reflect.KType

@PublishedApi
internal class PresenterNode(
  producer: ViewModelProducer<ViewModel>,
  type: KType,
) : ViewModelRef(type), ViewModelProducerScope {
  var modifierChain: PresenterModifier = PresenterModifier

  var producer: ViewModelProducer<ViewModel> by mutableStateOf(producer)
  val children = SnapshotStateList<PresenterNode>()

  override val viewModel = derivedStateOf {
    with(producer) {
      produce(children)
    }
  }

  override fun ViewModelRef.resolve(flatten: Boolean): ViewModel =
    if (flatten) resolveRecursively() else viewModel.value

  fun disposeChildren() {
    children.clear()
  }
}
