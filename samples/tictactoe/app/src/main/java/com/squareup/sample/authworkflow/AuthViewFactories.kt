package com.squareup.sample.authworkflow

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.ViewRegistry

@OptIn(WorkflowUiExperimentalApi::class)
val AuthViewFactories = ViewRegistry(
    AuthorizingViewFactory,
    LoginViewFactory,
    SecondFactorViewFactory
)
