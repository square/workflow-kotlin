package com.squareup.sample.authworkflow

import com.squareup.sample.tictactoe.databinding.AuthorizingLayoutBinding
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.forViewBinding
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
internal val AuthorizingViewFactory: ScreenViewFactory<AuthorizingScreen> =
  forViewBinding(AuthorizingLayoutBinding::inflate) { rendering, _ ->
    authorizingMessage.text = rendering.message
  }
