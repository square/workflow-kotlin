package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * @see [BaseRenderContext]. This is the version which adds support for the Compose optimized
 * runtime.
 */
public interface BaseComposeRenderContext<PropsT, StateT, OutputT> :
  BaseRenderContext<PropsT, StateT, OutputT> {

  /**
   * @see [BaseRenderContext.renderChild] as this is equivalent, except as a Composable.
   */
  @Composable
  public fun <ChildPropsT, ChildOutputT, ChildRenderingT> ChildRendering(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT

  /**
   * @see [BaseRenderContext.runningSideEffect] as this is equivalent, except as a Composable.
   */
  @Composable
  public fun RunningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  )
}

/**
 * Convenience alias of [BaseComposeRenderContext.ChildRendering] for workflows that don't take props.
 */
@Composable
public fun <PropsT, StateT, OutputT, ChildOutputT, ChildRenderingT>
BaseComposeRenderContext<PropsT, StateT, OutputT>.ChildRendering(
  child: Workflow<Unit, ChildOutputT, ChildRenderingT>,
  key: String = "",
  handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
): ChildRenderingT = ChildRendering(child, Unit, key, handler)
/**
 * Convenience alias of [BaseComposeRenderContext.ChildRendering] for workflows that don't emit output.
 */
@Composable
public fun <PropsT, ChildPropsT, StateT, OutputT, ChildRenderingT>
BaseComposeRenderContext<PropsT, StateT, OutputT>.ChildRendering(
  child: Workflow<ChildPropsT, Nothing, ChildRenderingT>,
  props: ChildPropsT,
  key: String = "",
): ChildRenderingT = ChildRendering(child, props, key) { noAction() }
/**
 * Convenience alias of [BaseComposeRenderContext.ChildRendering] for children that don't take props or emit
 * output.
 */
@Composable
public fun <PropsT, StateT, OutputT, ChildRenderingT>
BaseComposeRenderContext<PropsT, StateT, OutputT>.ChildRendering(
  child: Workflow<Unit, Nothing, ChildRenderingT>,
  key: String = "",
): ChildRenderingT = ChildRendering(child, Unit, key) { noAction() }

/**
 * Ensures a [Worker] that never emits anything is running. Since [worker] can't emit anything,
 * it can't trigger any [WorkflowAction]s.
 *
 * If your [Worker] does not output anything, then simply use [runningSideEffect].
 *
 * @param key An optional string key that is used to distinguish between identical [Worker]s.
 */
@Composable
public inline fun <reified W : Worker<Nothing>, PropsT, StateT, OutputT>
BaseComposeRenderContext<PropsT, StateT, OutputT>.RunningWorker(
  worker: W,
  key: String = ""
) {
  RunningWorker(worker, key) {
    // The compiler thinks this code is unreachable, and it is correct. But we have to pass a lambda
    // here so we might as well check at runtime as well.
    @Suppress("UNREACHABLE_CODE", "ThrowableNotThrown")
    throw AssertionError("Worker<Nothing> emitted $it")
  }
}

/**
 * Ensures [worker] is running. When the [Worker] emits an output, [handler] is called
 * to determine the [WorkflowAction] to take. When the worker finishes, nothing happens (although
 * another render pass may be triggered).
 *
 * Like workflows, workers are kept alive across multiple render passes if they're the same type,
 * and different workers of distinct types can be run concurrently. However, unlike workflows,
 * workers are compared by their _declared_ type, not their actual type. This means that if you
 * pass a worker stored in a variable to this function, the type that will be used to compare the
 * worker will be the type of the variable, not the type of the object the variable refers to.
 *
 * @param key An optional string key that is used to distinguish between identical [Worker]s.
 */
@Composable
public inline fun <T, reified W : Worker<T>, PropsT, StateT, OutputT>
BaseComposeRenderContext<PropsT, StateT, OutputT>.RunningWorker(
  worker: W,
  key: String = "",
  noinline handler: (T) -> WorkflowAction<PropsT, StateT, OutputT>
) {
  RunningWorker(worker, typeOf<W>(), key, handler)
}

/**
 * Ensures [worker] is running. When the [Worker] emits an output, [handler] is called
 * to determine the [WorkflowAction] to take. When the worker finishes, nothing happens (although
 * another render pass may be triggered).
 *
 * @param workerType `typeOf<W>()`
 * @param key An optional string key that is used to distinguish between identical [Worker]s.
 */
@PublishedApi
@Composable
internal fun <T, PropsT, StateT, OutputT>
BaseComposeRenderContext<PropsT, StateT, OutputT>.RunningWorker(
  worker: Worker<T>,
  workerType: KType,
  key: String = "",
  handler: (T) -> WorkflowAction<PropsT, StateT, OutputT>
) {
  val workerWorkflow = remember(workerType, key) { ComposeWorkerWorkflow<T>(workerType, key) }
  ChildRendering(workerWorkflow, props = worker, key = key, handler = handler)
}
