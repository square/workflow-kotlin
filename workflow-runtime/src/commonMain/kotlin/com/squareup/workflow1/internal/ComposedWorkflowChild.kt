package com.squareup.workflow1.internal

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.RememberObserver
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import kotlinx.coroutines.CoroutineScope

internal class ComposedWorkflowChild<ChildOutputT, ParentPropsT, ParentOutputT, ParentRenderingT>(
  compositeHashKey: Int,
  private val coroutineScope: CoroutineScope,
  private val compositionContext: CompositionContext,
  private val recomposeScope: RecomposeScope
) : RememberObserver {
  val workflowKey: String = "composed-workflow:${compositeHashKey.toString(radix = 16)}"
  private var disposed = false

  var onOutput: ((ChildOutputT) -> Unit)? = null
  val handler: (ChildOutputT) -> WorkflowAction<ParentPropsT, ParentOutputT, ParentRenderingT> =
    { output ->
      action(workflowKey) {
        // This action is being applied to the composition host workflow, which we don't want to
        // update at all.
        // The onOutput callback instead will update any compose snapshot state required.
        // Technically we could probably invoke it directly from the handler, not wait until the
        // queued action is processed, but this ensures consistency with the rest of the workflow
        // runtime: the callback won't fire before other callbacks ahead in the queue.
        // We check disposed since a previous update may have caused a recomposition that removed
        // this child from composition and since it doesn't have its own channel, we have to no-op.
        if (!disposed) {
          onOutput?.invoke(output)
        }

        // TODO After invoking callback, send apply notifications and check if composition has any
        //  invalidations. Iff it does, then mark the current workflow node as needing re-render
        //  regardless of state change.
      }
    }

  override fun onAbandoned() {
    onForgotten()
  }

  override fun onRemembered() {
  }

  override fun onForgotten() {
    disposed = true
    TODO("notify parent that we're gone")
  }
}
