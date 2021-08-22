package com.squareup.sample.compose.mutablestateworkflow

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.sample.compose.mutablestateworkflow.HelloMutableStateWorkflow.Rendering
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.composeViewFactory
import com.squareup.workflow1.ui.compose.tooling.Preview

@OptIn(WorkflowUiExperimentalApi::class)
val HelloMutableStateBinding = composeViewFactory<Rendering> { rendering, _ ->
  Button(onClick = rendering.onClick) {
    Text(rendering.text)
  }
}

@OptIn(WorkflowUiExperimentalApi::class)
@Preview(heightDp = 150, showBackground = true)
@Composable fun HelloMutableStateBindingPreview() {
  HelloMutableStateBinding.Preview(Rendering("Hello!", onClick = {}))
}
