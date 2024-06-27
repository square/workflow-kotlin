package com.squareup.sample.compose.hellocompose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.ComposeScreen

@OptIn(WorkflowUiExperimentalApi::class)
data class HelloComposeScreen(
  val message: String,
  val onClick: () -> Unit
) : ComposeScreen {
  @Composable override fun Content(viewEnvironment: ViewEnvironment) {
    Text(
      message,
      modifier = Modifier
        .clickable(onClick = onClick)
        .fillMaxSize()
        .wrapContentSize(Alignment.Center)
    )
  }
}
