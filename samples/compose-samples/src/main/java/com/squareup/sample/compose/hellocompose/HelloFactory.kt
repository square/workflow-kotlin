package com.squareup.sample.compose.hellocompose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.squareup.sample.compose.hellocompose.HelloWorkflow.Rendering
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.composableFactory
import com.squareup.workflow1.visual.VisualFactory
import com.squareup.workflow1.visual.widen

@WorkflowUiExperimentalApi
val helloFactory: VisualFactory<Unit, Any, (Modifier) -> Unit> =
  composableFactory<Rendering> { rendering, _ ->
    Text(
      rendering.message,
      modifier = Modifier
        .clickable(onClick = rendering.onClick)
        .fillMaxSize()
        .wrapContentSize(Alignment.Center)
    )
  }.widen()
