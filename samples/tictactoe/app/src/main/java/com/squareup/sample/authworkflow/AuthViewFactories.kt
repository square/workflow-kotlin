package com.squareup.sample.authworkflow

import com.squareup.workflow1.ui.ViewRegistry

val AuthViewFactories = ViewRegistry(
  AuthorizingViewFactory,
  LoginViewFactory,
  SecondFactorViewFactory
)
