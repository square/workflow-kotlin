package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.visual.ExactTypeVisualFactory
import com.squareup.workflow1.visual.VisualEnvironmentKey
import com.squareup.workflow1.visual.VisualFactory

/**
 * Registering visual registries in the environment.
 */
@WorkflowUiExperimentalApi
public object ComposableFactoryKey : VisualEnvironmentKey<VisualFactory<Unit, Any, ComposableLambda>>() {
  override val default: VisualFactory<Unit, Any, ComposableLambda>
    get() = ExactTypeVisualFactory<Unit, @Composable (Modifier) -> Unit>()
}
