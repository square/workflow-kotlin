package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composer

/**
 * Group keys for the groups that the compose runtime creates by calling [Composer] group APIs
 * directly instead of letting the Compose compiler generate them.
 *
 * The Compose compiler gives every group it emits a key derived from the group's source location.
 * Code that starts groups by hand has to supply its own key, so this object is the single place
 * those keys are defined.
 *
 * ## Scheme
 *
 * Each key's value is the [String.hashCode] of its fully-qualified name, e.g.
 * `"com.squareup.workflow1.internal.compose.ComposeGroupKeys.RENDER_WORKFLOW_RESTART_GROUP"
 * .hashCode()`. Kotlin specifies `String.hashCode` identically on every platform, so the value is
 * the same on all targets. The values are written out as literals because `const val`s can't call
 * functions; `ComposeGroupKeysTest` checks that each literal matches its name, and fails with the
 * expected value when a key is added or renamed.
 *
 * ## Constraints
 *
 * - A key only has to differ from the keys of the groups it can appear next to under the same
 *   parent. It doesn't need to be globally unique, and colliding with a compiler-generated key is
 *   harmless.
 * - A restart group's recompose lambda must restart with the same key the group was started with.
 * - Keys feed into [androidx.compose.runtime.currentCompositeKeyHashCode], which is used to key
 *   saved state. Changing a key changes the saved state key of everything below that group.
 */
internal object ComposeGroupKeys {
  /**
   * Restart group that wraps the whole body of `renderWorkflowRestartableImpl` in
   * `RenderWorkflow.kt`. It defines the recompose scope for a single workflow's render pass.
   */
  const val RENDER_WORKFLOW_RESTART_GROUP = -729043181

  /**
   * Replace group nested in [RENDER_WORKFLOW_RESTART_GROUP] around the call to
   * [ComposeRenderContext.renderSelf], so that render can be skipped while the rest of the restart
   * group (reading the cached rendering) still runs.
   */
  const val RENDER_WORKFLOW_SKIP_GROUP = 1778437915

  /**
   * Movable group that [ComposeRenderContext] puts around each child, side effect, and remembered
   * value. All _types_ of children must use the same key so that they can be moved among each other
   * correctly; the data keys passed alongside it distinguish them.
   */
  const val RENDER_CONTEXT_CHILD_GROUP = -218804824
}
