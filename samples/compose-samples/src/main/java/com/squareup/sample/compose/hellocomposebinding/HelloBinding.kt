package com.squareup.sample.compose.hellocomposebinding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.sample.compose.hellocomposebinding.HelloWorkflow.Rendering
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.ScreenComposableFactory
import com.squareup.workflow1.ui.compose.tooling.Preview

@OptIn(WorkflowUiExperimentalApi::class)
val HelloBinding = ScreenComposableFactory<Rendering> { rendering ->
  Text(
    rendering.message,
    modifier = Modifier
      .fillMaxSize()
      .clickable(onClick = rendering.onClick)
      .wrapContentSize()
  )
}

@OptIn(WorkflowUiExperimentalApi::class)
@Preview(heightDp = 150, showBackground = true)
@Composable
fun DrawHelloRenderingPreview() {
  HelloBinding.Preview(Rendering("Hello!", onClick = {}))
}
