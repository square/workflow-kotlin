package com.squareup.sample.compose.presenterdemo.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.squareup.sample.compose.presenterdemo.structurednav.BackstackPresenter
import com.squareup.workflow1.presenter.PresenterModifier

@Composable
fun AuthFlowPresenter(authService: AuthService) {
  var inStage2 by remember { mutableStateOf(false) }

  BackstackPresenter {
    AuthStage1Presenter(
      authService,
      onStage2Required = { inStage2 = true },
    )

    if (inStage2) {
      AuthStage2Presenter(
        authService,
        modifier = PresenterModifier.onBackRequested { inStage2 = false }
      )
    }
  }
}
