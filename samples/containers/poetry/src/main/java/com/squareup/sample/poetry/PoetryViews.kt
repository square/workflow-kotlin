package com.squareup.sample.poetry

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.ViewRegistry

@OptIn(WorkflowUiExperimentalApi::class)
val PoetryViews = ViewRegistry(StanzaListLayoutRunner, StanzaLayoutRunner)
