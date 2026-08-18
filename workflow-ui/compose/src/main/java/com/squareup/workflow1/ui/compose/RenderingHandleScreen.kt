package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.RenderingHandle
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.ui.Screen

/**
 * Wraps a [RenderingHandle] returned by [BaseRenderContext.renderWorkflowIndirectly] so that it can
 * be displayed anywhere a [Screen] can – put it in a `BackStackScreen`, hand it to
 * [WorkflowRendering], and so on.
 *
 * A [RenderingHandle] can't implement [Screen] itself: it is declared in `workflow-core`, which is
 * pure Kotlin and knows nothing about the view layer. This wrapper lives in the Compose integration
 * instead, which is also where the automatic invalidation comes from – reading
 * [RenderingHandle.currentRendering] from a composition subscribes to it, so when the child
 * workflow renders again this composable recomposes without the parent workflow being involved at
 * all.
 *
 * Note that this only works for children whose renderings are [Screen]s. Classic (non-Compose)
 * containers can't show a handle yet.
 *
 * @param handle The handle to display. Since the runtime returns the same instance for the lifetime
 * of the child's session, it is safe (and cheap) to build a new [RenderingHandleScreen] around it
 * on every render pass.
 */
@WorkflowExperimentalApi
public class RenderingHandleScreen(
  public val handle: RenderingHandle
) : ComposeScreen {

  @Composable override fun Content() {
    // Snapshot state read: the runtime writing a new rendering to the handle invalidates just this
    // composable.
    val rendering = handle.currentRendering
    require(rendering is Screen) {
      "Expected an indirectly rendered workflow to render a Screen, but found $rendering."
    }
    WorkflowRendering(rendering)
  }

  override fun toString(): String = "RenderingHandleScreen($handle)"
}
