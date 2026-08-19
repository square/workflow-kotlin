package com.squareup.workflow1.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Emits [viewModel] directly as the view model to the parent presenter on the default presenter
 * slot.
 *
 * If you need to read state to create [viewModel], then use the overload of [Presenter] that takes
 * a producer lambda instead. This allows only the producer to restart when state changes, instead
 * of recomposing the composable you're calling [Presenter] from.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun <T : Any> Presenter(
  viewModel: T,
  modifier: PresenterModifier = PresenterModifier,
) {
  Presenter(
    producer = { viewModel },
    modifier = modifier
  )
}

/**
 * Emits the single view model returned from [producer] on the default slot.
 */
@Composable
inline fun <T : Any> Presenter(
  modifier: PresenterModifier = PresenterModifier,
  crossinline producer: () -> T
) {
  Presenter(
    modifier = modifier,
    presenterPolicy = {
      outputSlots[defaultSlot] = producer()
    }
  )
}

/**
 * Emits a presenter node whose [PresenterPolicy] can emit view models on multiple slots.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun Presenter(
  modifier: PresenterModifier = PresenterModifier,
  presenterPolicy: PresenterPolicy,
) {
  NavigationPresenter(
    modifier = modifier,
    presenterPolicy = presenterPolicy,
    content = {}
  )
}

/**
 * Emits a presenter node whose [PresenterPolicy] can emit view models on multiple slots.
 *
 * All nodes emitted by [content] are given to the [presenterPolicy] in a list. Each child is
 * represented by a [ChildPresenter] which can be used to access the view models the child
 * publishes.
 */
@Composable
inline fun NavigationPresenter(
  presenterPolicy: PresenterPolicy,
  modifier: PresenterModifier = PresenterModifier,
  content: @Composable () -> Unit
) {
  // This is currently only used for defaultSlot, but since that local is not static, that's a state
  // read, so store the whole map in the node to defer the state read until the presenter phase.
  val compositionLocals = currentComposer.currentCompositionLocalMap
  ComposeNode<PresenterNode, PresenterApplier>(
    factory = { PresenterNode(policy = presenterPolicy) },
    update = {
      set(compositionLocals) { this.compositionLocals = it }
      set(modifier) { this.modifierChain = it }
      update(presenterPolicy) { this.producer = it }
    },
    content = content
  )
}

/**
 * Emits a presenter node that publishes the return value of [content] on the default slot.
 *
 * [content] must not emit any presenters. To emit presenters from inside [content] wrap them in
 * [subcomposePresenter].
 */
// Do NOT make this inline. It is intentionally not inline to create a recompose boundary, since
// content returns a value it doesn't have its own.
@Composable
fun <T : Any> ProducingPresenter(
  modifier: PresenterModifier = PresenterModifier,
  slot: PresenterSlot<in T> = LocalDefaultPresenterSlot.current,
  content: @Composable () -> T
) {
  var viewModel: T? by remember { mutableStateOf(null) }
  NavigationPresenter(
    presenterPolicy = {
      outputSlots[slot] = viewModel!!
    },
    modifier = modifier
  ) {
    viewModel = content()
  }
}

/**
 * Emits a presenter node and returns a ref that can be stored in a view model and will resolve to
 * the node's view model.
 *
 * This function is useful as an escape hatch from [ProducingPresenter].
 */
// TODO remove this as public api? Can be exposed in presenter phase similarly to SubcomposeLayout.
//  That doesn't work for escaping from ProducingPresenter but that could also be exposed only to
//  that composable's content.
@Composable
fun subcomposePresenter(
  rootPresenterPolicy: PresenterPolicy = DefaultRootPresenterPolicy,
  content: @Composable () -> Unit
): ChildPresenter {
  val context = rememberCompositionContext()
  val updatedPolicy by rememberUpdatedState(rootPresenterPolicy)
  val subComposition = remember {
    PresenterComposition(
      parent = context,
      rootProducer = { children ->
        with(updatedPolicy) {
          produce(children)
        }
      }
    )
  }
  subComposition.setContent(content)
  return subComposition.rootNode
}
