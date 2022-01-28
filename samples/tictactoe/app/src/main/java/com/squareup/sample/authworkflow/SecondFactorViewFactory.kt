package com.squareup.sample.authworkflow

import com.squareup.sample.tictactoe.databinding.SecondFactorLayoutBinding
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backPressedHandler

@OptIn(WorkflowUiExperimentalApi::class)
internal val SecondFactorViewFactory: ViewFactory<SecondFactorScreen> =
  LayoutRunner.bind(SecondFactorLayoutBinding::inflate) { rendering, _ ->
    root.backPressedHandler = { rendering.onCancel() }
    secondFactorToolbar.setNavigationOnClickListener { rendering.onCancel() }

    secondFactorErrorMessage.text = rendering.errorMessage

    secondFactorSubmitButton.setOnClickListener {
      rendering.onSubmit(secondFactor.text.toString())
    }
  }
