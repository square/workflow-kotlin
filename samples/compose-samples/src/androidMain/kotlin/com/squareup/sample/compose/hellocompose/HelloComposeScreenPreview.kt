package com.squareup.sample.compose.hellocompose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.tooling.Preview

@OptIn(WorkflowUiExperimentalApi::class)
@Preview(heightDp = 150, showBackground = true)
@Composable
private fun HelloPreview() {
  HelloComposeScreen(
    "Hello!",
    onClick = {}
  ).Preview()
}
