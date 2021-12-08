package com.squareup.sample.todo.managedstate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.squareup.workflow1.ui.TextController
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
@Composable fun TextController.onTextChanged(block: (String) -> Unit) {
  onTextChanged.collectAsState(null).value?.run(block)
}
