package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
public object CoveredByModal : ViewEnvironmentKey<Boolean>(type = Boolean::class) {
  override val default: Boolean = false
}
