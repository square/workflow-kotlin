package com.squareup.sample.compose.hellocomposebinding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.sample.compose.hellocomposebinding.HelloWorkflow.Rendering
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.composeViewFactory

@OptIn(WorkflowUiExperimentalApi::class)
val HelloBinding = composeViewFactory<Rendering> { rendering, _ ->
  Text(
    rendering.message,
    modifier = Modifier
      .fillMaxSize()
      .clickable(onClick = rendering.onClick)
      .wrapContentSize(Alignment.Center)
  )
}

@OptIn(WorkflowUiExperimentalApi::class)
@Preview(heightDp = 150, showBackground = true)
@Composable fun DrawHelloRenderingPreview() {
  // TODO(#458) Uncomment once preview support is imported.
  // HelloBinding.preview(Rendering("Hello!", onClick = {}))
}
