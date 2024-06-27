package com.squareup.sample.compose.hellocomposebinding

import androidx.compose.material.MaterialTheme
import com.squareup.sample.compose.defaultViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.withCompositionRoot
import com.squareup.workflow1.ui.plus

@OptIn(WorkflowUiExperimentalApi::class)
val viewEnvironment =
  (defaultViewEnvironment() + ViewRegistry(HelloBinding))
    .withCompositionRoot { content ->
      MaterialTheme(content = content)
    }
