package com.squareup.workflow1.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlin.reflect.typeOf

/**
 * Emits [viewModel] directly as the view model to the parent presenter.
 *
 * If you need to read state to create [viewModel], then use the overload of [Presenter] without
 * a `content` lambda instead. This allows only the producer to restart when state changes, instead
 * of recomposing the composable you're calling [Present] from.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun Present(
  viewModel: ViewModel,
  modifier: PresenterModifier = PresenterModifier,
) {
  Presenter(viewModelProducer = { viewModel }, modifier = modifier)
}

/**
 * Emits a presenter node that produces a [ViewModel] by calling [viewModelProducer] with no child
 * nodes.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun Presenter(
  modifier: PresenterModifier = PresenterModifier,
  viewModelProducer: ViewModelProducer<ViewModel>,
) {
  Presenter(viewModelProducer = viewModelProducer, modifier = modifier) {}
}

/**
 * Emits a presenter node that produces a [ViewModel] by calling [viewModelProducer] with the
 * child nodes emitted by [content].
 */
@Composable
inline fun <reified T : ViewModel> Presenter(
  viewModelProducer: ViewModelProducer<T>,
  modifier: PresenterModifier = PresenterModifier,
  content: @Composable () -> Unit
) {
  val type = typeOf<T>()
  key(type) {
    ComposeNode<PresenterNode, PresenterApplier>(
      factory = { PresenterNode(viewModelProducer, type) },
      update = {
        update(viewModelProducer) { this.producer = it }
        set(modifier) { this.modifierChain = it }
      },
      content = content
    )
  }
}

/**
 * Emits a presenter node, with [content] as a child, and using the return value of [content] as the
 * node's view model.
 *
 * [content] must not emit any presenters. To emit presenters from inside [content] wrap them in
 * [subcomposePresenter].
 */
// Do NOT make this inline. It is intentionally not inline to create a recompose boundary, since
// content returns a value it doesn't have its own.
@Composable
fun ProducingPresenter(
  modifier: PresenterModifier = PresenterModifier,
  content: @Composable () -> ViewModel
) {
  var viewModel: ViewModel? by remember { mutableStateOf(null) }
  Presenter(viewModelProducer = { viewModel!! }, modifier = modifier) {
    viewModel = content()
  }
}

/**
 * Emits a presenter node and returns a ref that can be stored in a view model and will resolve to
 * the node's view model. The emitted node will be excluded from the parent node's list of children
 * inside the [ViewModelProducer.produce] function.
 *
 * This function is useful as an escape hatch from [ProducingPresenter].
 */
@Composable
fun subcomposePresenter(
  content: @Composable () -> Unit
): ViewModelRef {
  val context = rememberCompositionContext()
  val subComposition = remember { PresenterComposition(context) }
  subComposition.setContent(content)
  return subComposition.ref
}
