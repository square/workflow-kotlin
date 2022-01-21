package com.squareup.sample.authworkflow

import com.squareup.sample.tictactoe.databinding.AuthorizingLayoutBinding
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
internal val AuthorizingViewFactory: ViewFactory<AuthorizingScreen> =
  LayoutRunner.bind(AuthorizingLayoutBinding::inflate) { rendering, _ ->
    authorizingMessage.text = rendering.message
  }
