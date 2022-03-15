package com.squareup.sample.authworkflow

import com.squareup.sample.tictactoe.databinding.LoginLayoutBinding
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner.Companion.bind
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler

@OptIn(WorkflowUiExperimentalApi::class)
internal val LoginViewFactory: ScreenViewFactory<LoginScreen> =
  bind(LoginLayoutBinding::inflate) { rendering, _ ->
    loginErrorMessage.text = rendering.errorMessage

    loginButton.setOnClickListener {
      rendering.onLogin(loginEmail.text.toString(), loginPassword.text.toString())
    }

    root.backPressedHandler = { rendering.onCancel() }
  }
