package com.squareup.workflow1.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Launches [presenter] into a composition that produces a lazy tree of [ViewModelRef]s. The root
 * of the tree is returned, along with a [ViewModelResolver] that can be used to resolve the refs
 * into [ViewModel]s.
 */
@OptIn(ExperimentalComposeApi::class)
fun present(
  scope: CoroutineScope,
  presenter: @Composable () -> Unit
): Pair<ViewModelRef, ViewModelResolver> {
  val recomposer = Recomposer(effectCoroutineContext = scope.coroutineContext)
  val composition = PresenterComposition(parent = recomposer)

  scope.launch {
    recomposer.runRecomposeAndApplyChanges()
  }

  composition.setContent(presenter)

  val resolver = object : ViewModelResolver {
    override fun resolveAsState(
      ref: ViewModelRef,
      resolveRecursively: Boolean
    ): State<ViewModel> = if (resolveRecursively) ref.asFlattenedState() else ref.viewModel
  }

  return Pair(composition.ref, resolver)
}
