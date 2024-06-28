package com.squareup.sample.compose.hellocomposebinding

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.sample.compose.hellocomposebinding.HelloWorkflow.Rendering
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.tooling.Preview

@OptIn(WorkflowUiExperimentalApi::class)
@Preview(heightDp = 150, showBackground = true)
@Composable
fun DrawHelloRenderingPreview() {
  HelloBinding.Preview(Rendering("Hello!", onClick = {}))
}
