@file:OptIn(WorkflowExperimentalApi::class)

package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.identifier

/** Typealias for the signature of [renderChild]. */
@WorkflowExperimentalApi
public typealias ComposableRenderChildFunction<PropsT, OutputT, RenderingT> = @Composable (
  childWorkflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: ((OutputT) -> Unit)?,
) -> RenderingT

/**
 * Intercepts calls to [com.squareup.workflow1.compose.renderChild].
 * See [ComposeWorkflowInterceptor.renderChild] for more info.
 * Install an interceptor by providing a [WorkflowComposableRuntimeConfig] via
 * [LocalWorkflowComposableRuntimeConfig].
 *
 * Note that this interface is [Stable] and so implementations must comply with the contract of that
 * annotation.
 */
@WorkflowExperimentalApi
@Stable
public interface ComposeWorkflowInterceptor {

  /**
   * Called every time [com.squareup.workflow1.compose.renderChild] renders a workflow.
   *
   * Every "instance" of this method in composition is guaranteed to only ever be called with
   * compatible instances of [childWorkflow]. That is, the current and previous values will have the
   * same [identifier]. This means implementations do not need to worry about
   * [keying off][androidx.compose.runtime.key] the workflow identifier themselves.
   *
   * Implementations of this method must follow some rules:
   * - They MUST NOT call [proceed] more than once.
   * - They MAY choose to not call [proceed] at all.
   * - They MUST always call [proceed] from the same "position" in composition. Since Compose
   *   memoizes state positionally, moving the call around will reset the state for the entire
   *   workflow subtree. If you must move the call around, first try to refactor your code to avoid
   *   that. If you _really must_ move the call around, use [movableContentOf] but beware this has
   *   extra cost.
   */
  @Composable
  fun <PropsT, OutputT, RenderingT> renderChild(
    childWorkflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT,
    onOutput: ((OutputT) -> Unit)?,
    proceed: ComposableRenderChildFunction<PropsT, OutputT, RenderingT>
  ): RenderingT
}

/**
 * Calls [ComposeWorkflowInterceptor.renderChild] with some checks to ensure the
 * [ComposeWorkflowInterceptor] is complying with its contract:
 *
 * - Throws [IllegalStateException] if [proceed] is called more than once.
 * - Throws [IllegalStateException] if [proceed] is called from a different position.
 */
@Composable
internal inline fun <P, O, R> ComposeWorkflowInterceptor.renderChildSafely(
  childWorkflow: Workflow<P, O, R>,
  props: P,
  noinline onOutput: ((O) -> Unit)?,
  crossinline proceed: ComposableRenderChildFunction<P, O, R>
): R = this.renderChild(
  childWorkflow = childWorkflow,
  props = props,
  onOutput = onOutput,
  // detectProceedMoved must be outside detectMultipleCalls because it needs to persist state
  // across recompositions.
  proceed = detectProceedMoved(
    detectMultipleProceedCalls(
      proceed
    )
  )
)

@Composable
private inline fun <P, O, R> detectProceedMoved(
  crossinline proceed: ComposableRenderChildFunction<P, O, R>
): ComposableRenderChildFunction<P, O, R> {
  // This is intentionally _not_ a snapshot state object. We force the int to box since there are
  // no "invalid" values for currentCompositeKeyHash that we could use as uninitialized sentinel.
  val initialKeyHolder = remember { arrayOfNulls<Int>(1) }
  return { innerWorkflow, innerProps, innerOnOutput ->
    val key = currentCompositeKeyHash
    if (initialKeyHolder[0] == null) {
      initialKeyHolder[0] = key
    } else if (key != initialKeyHolder[0]) {
      error(
        "Detected ComposeWorkflowInterceptor moved its call to proceed between compositions. " +
          "Proceed must always be called from the same place in composition to preserve the " +
          "state of the workflow subtree. If you really need to move the proceed call around, " +
          "use movableContentOf."
      )
    }
    proceed(innerWorkflow, innerProps, innerOnOutput)
  }
}

private val ProceedCalledAgainError: ComposableRenderChildFunction<Nothing, Nothing, Nothing> =
  { _, _, _ ->
    error(
      "Detected multiple calls to proceed from the same invocation of ComposeWorkflowInterceptor." +
        " Proceed must only be called once per interceptor invocation per composition."
    )
  }

// Impl note: This is a non-composable function to avoid Compose auto-memoizing the lambdas.
// TODO does this still work if it's inline?
private inline fun <P, O, R> detectMultipleProceedCalls(
  crossinline proceed: ComposableRenderChildFunction<P, O, R>
): ComposableRenderChildFunction<P, O, R> {
  var proceedOnce: ComposableRenderChildFunction<P, O, R>
  proceedOnce = { innerWorkflow, innerProps, innerOnOutput ->
    proceed(innerWorkflow, innerProps, innerOnOutput)
      .also {
        @Suppress("UNCHECKED_CAST")
        proceedOnce = ProceedCalledAgainError as ComposableRenderChildFunction<P, O, R>
      }
  }

  // Don't return proceedOnce directly! It is critical that we re-read the var every time it's
  // called.
  return { innerWorkflow, innerProps, innerOnOutput ->
    proceedOnce(innerWorkflow, innerProps, innerOnOutput)
  }
}
