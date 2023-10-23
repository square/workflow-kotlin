package com.squareup.sample.compose.nestedrenderings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.sample.compose.databinding.LegacyViewBinding
import com.squareup.sample.compose.nestedrenderings.RecursiveWorkflow.LegacyRendering
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.tooling.Preview

/**
 * A [ScreenViewRunner] that renders [LegacyRendering]s using the legacy view framework.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class LegacyRunner(private val binding: LegacyViewBinding) : ScreenViewRunner<LegacyRendering> {

  override fun showRendering(
    rendering: LegacyRendering,
    environment: ViewEnvironment
  ) {
    binding.stub.show(rendering.rendering, environment)
  }
}

@OptIn(WorkflowUiExperimentalApi::class)
@Preview(widthDp = 200, heightDp = 150, showBackground = true)
@Composable
private fun LegacyRunnerPreview() {
  LegacyRendering(StringRendering("child")).Preview(
    placeholderModifier = Modifier.fillMaxSize()
  )
}
