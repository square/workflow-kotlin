package com.squareup.workflow1.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import kotlin.reflect.typeOf

internal class PresenterComposition internal constructor(
  parent: CompositionContext,
) : ViewModelProducer<ViewModel> {

  private val rootNode = PresenterNode(
    producer = this,
    type = typeOf<ViewModel>(),
  )

  private val composition = Composition(
    applier = PresenterApplier(rootNode),
    parent = parent
  )

  @Suppress("ConvertToExplicitBackingFields")
  val ref: ViewModelRef get() = rootNode

  fun setContent(content: @Composable () -> Unit) {
    composition.setContent(content)
  }

  override fun ViewModelProducerScope.produce(children: List<ViewModelRef>): ViewModel =
    children.simplify()
}
