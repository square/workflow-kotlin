package com.squareup.workflow2

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeCompilerApi
import androidx.compose.runtime.CompositionReference
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.EmbeddingContext
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionFor
import androidx.compose.runtime.compositionReference
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withRunningRecomposer
import androidx.compose.ui.node.ExperimentalLayoutNodeApi
import androidx.compose.ui.node.LayoutNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.reflect.KClass

class WorkflowRendering<out R> internal constructor() {
  private var _value: MutableState<R>? = null

  /**
   * The current rendering value. Composables reading this property will automatically be recomposed
   * when it changes.
   */
  // UnsafeVariance is ok because set is internal.
  var value: @UnsafeVariance R
    get() = _value!!.value
    internal set(value) {
      if (_value == null) {
        _value = mutableStateOf(value)
      } else {
        _value!!.value = value
      }
    }
}

class ComposableRegistry(private val map: Map<KClass<*>, SelfRendering>) {
  fun getComposableForRendering(rendering: Any): SelfRendering {
    // Allow the map to override particular SelfRendering types.
    return map[rendering::class]
        ?: (rendering as? SelfRendering)
        ?: error("No rendering found for ${rendering::class}")
  }
}

@Composable fun WorkflowHost(
  registry: ComposableRegistry,
  workflow: @Composable () -> Any
) {
  WorkflowHost {
    val rendering = renderChild(workflow)
    remember(registry, rendering) {
      registry.getComposableForRendering(rendering)
    }
  }
}

@OptIn(ExperimentalComposeApi::class, ExperimentalLayoutNodeApi::class, ComposeCompilerApi::class)
@Composable fun WorkflowHost(workflow: @Composable () -> SelfRendering) {
  // val workflowState = rememberUpdatedState(workflow)
  // val scope = rememberCoroutineScope()
  // val rendering = remember { renderWorkflowIn(workflowState, scope) }
  // rendering.value.render()

  // val root = remember { Ref<LayoutNode>() }
  // val compositionRef = compositionReference()
  // emit<LayoutNode, Applier<Any>>(
  //     ctor = { LayoutNode() },
  //     update = {
  //       set(Unit, { root.value = this })
  //       // set(materialized, {this.modifier = it})
  //       // set(measureBlock, state.setMeasureBlock)
  //       set(DensityAmbient.current, { this.density = it })
  //       // set(LayoutDirectionAmbient.current, { this.layoutDirection = it })
  //     },
  // )
  // val composition = wrapperSubcomposeInto(root.value!!, compositionRef) {
  //   workflow()
  // }
  val reference = compositionReference()
  // val reference = Recomposer.current()
  val composer = currentComposer
  println("OMG composing SideEffect")

  // DisposableEffect(subject = Unit) {
  //   println("OMG SideEffect")
  // val applier = remember { WrapperApplier(composer.applier) }
  val applier = composer.applier
  val key = remember { Any() }

  doCompose(key, reference, applier, workflow)

  // val composition = wrapperSubcomposeInto(key, reference, applier) {
  //   println("OMG composing workflow")
  //   val rendering = workflow()
  //   println("OMG rendering workflow")
  //   rendering.render()
  // }

  // onDispose(composition::dispose)
  // }

  // val rendering = remember { mutableStateOf<SelfRendering?>(null) }
  // Layout({
  //   rendering.value = workflow()
  // }) { measurables, _ ->
  //   if (measurables.isNotEmpty()) {
  //     Log.w("WorkflowHost", "Workflow tried to compose UI (${measurables.size} nodes)")
  //   }
  //   layout(0, 0) {
  //     // Don't draw anything from the workflow.
  //   }
  // }
  // rendering.value!!.render()
}

@OptIn(ExperimentalComposeApi::class)
private fun doCompose(
  key: Any,
  reference: CompositionReference,
  applier: Applier<out Any?>,
  workflow: @Composable () -> SelfRendering
) {
  wrapperSubcomposeInto(key, reference, applier) {
    foo(workflow)
  }
}

// This extra layer of indirection seems to be necessary to prevent infinite looping.
@Composable private fun foo(workflow: @Composable () -> SelfRendering) {
  println("OMG composing workflow")
  val rendering = workflow()
  println("OMG rendering workflow")
  rendering.render()
}

@OptIn(ExperimentalComposeApi::class)
fun <R> renderWorkflowIn(
  workflowState: State<@Composable () -> R>,
  scope: CoroutineScope
): WorkflowRendering<R> {
  println("OMG renderWorkflowIn scope=$scope")
  val job = scope.coroutineContext[Job]!!
  val compositionReference = workflowRecomposer(scope)
  val workflowComposition = compositionFor(workflowState, Workflow2Applier, compositionReference)
  job.invokeOnCompletion {
    workflowComposition.dispose()
  }

  val rendering = WorkflowRendering<R>()
  workflowComposition.setContent {
    renderChildTo(workflowState.value, rendering)
  }

  return rendering
}

@Composable fun <R> renderChild(workflow: @Composable () -> R): WorkflowRendering<R> {
  val rendering = remember { WorkflowRendering<R>() }
  renderChildTo(workflow, rendering)
  return rendering
}

// class SelfRendering internal constructor(private val render: @Composable () -> Unit) {
//   @Composable fun render() {
//     render.invoke()
//   }
// }

interface SelfRendering {
  @Composable fun render()
}

// /**
//  * Note that this function should only be called from a workflow, but the lambda passed to it may
//  * emit to the hosting composition.
//  */
// @Composable fun SelfRendering(render: @Composable () -> Unit): WorkflowRendering<SelfRendering> {
//   return SelfRendering {
//     render()
//   }
// }

/**
 * This function returns unit to force it to become a recompose scope.
 */
@Composable internal fun <R> renderChildTo(
  workflow: @Composable () -> R,
  rendering: WorkflowRendering<R>
) {
  rendering.value = workflow().also {
    println("OMG renderChildTo updated value to $it")
  }
}

private fun workflowRecomposer(scope: CoroutineScope): Recomposer {
  // Copied from Recomposer.current(), but modified to actually use the passed-in scope.

  val embeddingContext = EmbeddingContext()
  val mainScope = scope + embeddingContext.mainThreadCompositionContext() + Unconfined

  return Recomposer(mainScope.coroutineContext, embeddingContext).also {
    mainScope.launch {
      it.runRecomposeAndApplyChanges()
    }
  }
}

@OptIn(ExperimentalComposeApi::class)
private object Workflow2Applier : Applier<Nothing> {
  override val current: Nothing get() = unsupported()

  override fun clear() {
    unsupported()
  }

  override fun down(node: Nothing) {
    unsupported()
  }

  override fun insert(
    index: Int,
    instance: Nothing
  ) {
    unsupported()
  }

  override fun move(
    from: Int,
    to: Int,
    count: Int
  ) {
    unsupported()
  }

  override fun remove(
    index: Int,
    count: Int
  ) {
    unsupported()
  }

  override fun up() {
    unsupported()
  }

  private fun unsupported(): Nothing = throw UnsupportedOperationException()
}