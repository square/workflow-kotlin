package com.squareup.sample.authworkflow

import android.view.LayoutInflater
import android.view.ViewGroup
import com.squareup.sample.tictactoe.databinding.SecondFactorLayoutBinding
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewUpdater
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
internal val SecondFactorViewFactory: ScreenViewFactory<SecondFactorScreen> =
  ScreenViewFactory.ofViewBinding<BindingT, ScreenT>({ inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean ->
    SecondFactorLayoutBinding.inflate(
      inflater,
      parent,
      attachToParent
    )
  }) { binding ->
    ScreenViewUpdater<ScreenT> { rendering, viewEnvironment ->
      binding.root.backPressedHandler = { rendering.onCancel() }
      binding.secondFactorToolbar.setNavigationOnClickListener { rendering.onCancel() }
      binding.secondFactorErrorMessage.text = rendering.errorMessage
      binding.secondFactorSubmitButton.setOnClickListener {
        rendering.onSubmit(binding.secondFactor.text.toString())
      }
    }
  }
