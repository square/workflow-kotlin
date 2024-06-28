package com.squareup.sample.compose.hellocomposebinding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import com.squareup.sample.compose.hellocomposebinding.HelloWorkflow.Rendering
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.ScreenComposableFactory

@OptIn(WorkflowUiExperimentalApi::class)
val HelloBinding = ScreenComposableFactory<Rendering> { rendering, _ ->
  Text(
    rendering.message,
    modifier = Modifier
      .fillMaxSize()
      .clickable(onClick = rendering.onClick)
      .wrapContentSize()
  )
}
