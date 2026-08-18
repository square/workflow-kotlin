package com.squareup.workflow1.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import com.squareup.workflow1.RenderingHandle
import com.squareup.workflow1.WorkflowExperimentalApi

/**
 * The traditional runtime's [RenderingHandle] implementation.
 *
 * [currentRendering] is backed by Compose snapshot state, so any composition that reads it – e.g.
 * `RenderingHandleScreen` – is invalidated automatically when the runtime writes a new rendering
 * to it. Note that using snapshot state here does _not_ require anything of the runtime itself: it
 * is only a notification mechanism, and workflows that never render indirectly never touch it.
 *
 * A single instance is created lazily by [WorkflowChildNode] the first time a child is rendered
 * indirectly, and lives exactly as long as that child's session does.
 */
@OptIn(WorkflowExperimentalApi::class)
internal class TraditionalRenderingHandle(initialRendering: Any?) : RenderingHandle() {
  override var currentRendering: Any? by mutableStateOf(
    value = initialRendering,
    // Renderings are compared by identity, not equality, exactly like RenderingAndSnapshot does for
    // the root rendering: two renderings can be `equals` and still close over different state (e.g.
    // event handlers), so dropping a new instance would leave the view layer stale.
    policy = referentialEqualityPolicy()
  )

  override fun toString(): String = "TraditionalRenderingHandle($currentRendering)"
}
