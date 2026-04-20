package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.identifier

/** Typealias for the signature of [renderChild]. */
internal typealias ComposableRenderChildFunction<PropsT, OutputT, RenderingT> = @Composable (
  childWorkflow: Workflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  onOutput: ((OutputT) -> Unit)?,
) -> RenderingT

/**
 * Intercepts calls to [com.squareup.workflow1.compose.renderChild].
 * See [ComposeWorkflowInterceptor.renderWorkflow] for more info.
 * Install an interceptor by providing a [WorkflowComposableRuntimeConfig] via
 * [LocalWorkflowComposableRuntimeConfig].
 *
 * Note that this interface is [Stable] and so implementations must comply with the contract of that
 * annotation.
 */
@Stable
internal interface ComposeWorkflowInterceptor {

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
  fun <PropsT, OutputT, RenderingT> renderWorkflow(
    childWorkflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT,
    onOutput: ((OutputT) -> Unit)?,
    proceed: ComposableRenderChildFunction<PropsT, OutputT, RenderingT>
  ): RenderingT
}
