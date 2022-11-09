package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.visual.VisualEnvironment
import com.squareup.workflow1.visual.VisualFactory
import com.squareup.workflow1.visual.VisualHolder

public typealias ComposableLambda = @Composable (Modifier) -> Unit
@WorkflowUiExperimentalApi
public typealias ComposableFactory<RenderingT> = VisualFactory<Unit, RenderingT, ComposableLambda>
@WorkflowUiExperimentalApi
public typealias ComposableHolder<RenderingT> = VisualHolder<RenderingT, ComposableLambda>

/**
 * A helper to create a ComposableFactory.
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> composableFactory(
  block: @Composable (RenderingT, Modifier) -> Unit
): ComposableFactory<RenderingT> =
  object : ComposableFactory<RenderingT> {
    override fun createOrNull(
      rendering: RenderingT,
      context: Unit,
      environment: VisualEnvironment
    ) = object : ComposableHolder<RenderingT> {
      // By changing the mutable state, any "update"
      val currentRendering = mutableStateOf(rendering)
      override val visual: ComposableLambda = { modifier ->
        block(currentRendering.value, modifier)
      }

      override fun update(rendering: RenderingT): Boolean {
        // By changing the mutable state, any composition using the "output" will be updated.
        currentRendering.value = rendering
        // TODO ?
        return true
      }
    }
  }

/**
 * A nicety to use holder.Content() instead of the generic output.
 */
@WorkflowUiExperimentalApi
@Composable
public fun VisualHolder<*, ComposableLambda>.Content(modifier: Modifier) {
  this.visual.invoke(modifier)
}
