package com.squareup.sample.authworkflow

import com.squareup.sample.tictactoe.databinding.SecondFactorLayoutBinding
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromViewBinding
import com.squareup.workflow1.ui.navigation.setBackHandler

internal val SecondFactorViewFactory: ScreenViewFactory<SecondFactorScreen> =
  fromViewBinding(SecondFactorLayoutBinding::inflate) { rendering, _ ->
    root.setBackHandler(rendering.onCancel)
    secondFactorToolbar.setNavigationOnClickListener { rendering.onCancel() }

    secondFactorErrorMessage.text = rendering.errorMessage

    secondFactorSubmitButton.setOnClickListener {
      rendering.onSubmit(secondFactor.text.toString())
    }
  }
