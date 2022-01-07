package com.squareup.sample.authworkflow

import android.view.LayoutInflater
import android.view.ViewGroup
import com.squareup.sample.tictactoe.databinding.LoginLayoutBinding
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewUpdater
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
internal val LoginViewFactory: ScreenViewFactory<LoginScreen> =
  ScreenViewFactory.ofViewBinding<BindingT, ScreenT>({ inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean ->
    LoginLayoutBinding.inflate(
      inflater,
      parent,
      attachToParent
    )
  }) { binding ->
    ScreenViewUpdater<ScreenT> { rendering, viewEnvironment ->
      binding.loginErrorMessage.text = rendering.errorMessage
      binding.loginButton.setOnClickListener {
        rendering.onLogin(binding.loginEmail.text.toString(), binding.loginPassword.text.toString())
      }
      binding.root.backPressedHandler = { rendering.onCancel() }
    }
  }
