package com.squareup.sample.compose.textinput

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.sample.compose.textinput.TextInputWorkflow.Rendering
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.tooling.Preview

@OptIn(WorkflowUiExperimentalApi::class)
@Preview(showBackground = true)
@Composable
private fun TextInputViewFactoryPreview() {
  TextInputViewFactory.Preview(
    Rendering(
      textController = TextController("Hello world"),
      onSwapText = {}
    )
  )
}
