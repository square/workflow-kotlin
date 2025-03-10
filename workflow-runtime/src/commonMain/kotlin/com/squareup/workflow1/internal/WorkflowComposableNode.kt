package com.squareup.workflow1.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import com.squareup.workflow1.WorkflowExperimentalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(WorkflowExperimentalApi::class)
internal class WorkflowComposableNode<RenderingT>(
  private val frameClock: MonotonicFrameClock, // TODO
  coroutineContext: CoroutineContext = EmptyCoroutineContext,
  private val saveableStateRegistry: SaveableStateRegistry, // TODO
  private val localsContext: CompositionLocalContext?, // TODO
) {
  private val coroutineContext = coroutineContext + frameClock
  private val recomposer: Recomposer = Recomposer(coroutineContext)
  private val composition: Composition = Composition(UnitApplier, recomposer)
  private val rendering = mutableStateOf<RenderingT?>(null)

  fun start() {
    // TODO I think we need more than a simple UNDISPATCHED start to make this work – we have to
    //  pump the dispatcher until the composition is finished.
    CoroutineScope(coroutineContext).launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        recomposer.runRecomposeAndApplyChanges()
      } finally {
        composition.dispose()
      }
    }
  }

  fun render(content: @Composable () -> RenderingT): RenderingT {
    composition.setContent {
      // Must provide the locals from the parent composition first so we can override the ones we
      // need. If it's null then there's no parent, but the CompositionLocalProvider API has no nice
      // way to pass nothing in this overload. I believe it's safe to actually call content through
      // two different code paths because whether there's a parent composition cannot change for an
      // existing workflow session – they can't move.
      if (localsContext == null) {
        LocalsProvider(content)
      } else {
        CompositionLocalProvider(localsContext) {
          LocalsProvider(content)
        }
      }
    }

    // TODO prime the first frame to generate the initial rendering

    @Suppress("UNCHECKED_CAST")
    return rendering.value as RenderingT
  }

  @Composable
  private inline fun LocalsProvider(crossinline content: @Composable () -> RenderingT) {
    CompositionLocalProvider(
      // LocalWorkflowCompositionHost provides this,
      LocalSaveableStateRegistry provides saveableStateRegistry,
    ) {
      rendering.value = content()
    }
  }
}
