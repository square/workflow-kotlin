package com.squareup.sample.authworkflow

import com.squareup.sample.tictactoe.databinding.LoginLayoutBinding
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromViewBinding
import com.squareup.workflow1.ui.navigation.setBackHandler

internal val LoginViewFactory: ScreenViewFactory<LoginScreen> =
  fromViewBinding(LoginLayoutBinding::inflate) { rendering, _ ->
    loginErrorMessage.text = rendering.errorMessage

    loginButton.setOnClickListener {
      rendering.onLogin(loginEmail.text.toString(), loginPassword.text.toString())
    }

    root.setBackHandler(rendering.onCancel)
  }
