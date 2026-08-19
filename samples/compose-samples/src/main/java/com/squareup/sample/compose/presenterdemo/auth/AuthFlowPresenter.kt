package com.squareup.sample.compose.presenterdemo.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.squareup.sample.compose.presenterdemo.backstack.BackStackPresenter

@Composable
fun AuthFlowPresenter(authService: AuthService) {
  var inStage2 by remember { mutableStateOf(false) }

  BackStackPresenter(
    onBack = { inStage2 = false }
  ) {
    AuthStage1Presenter(
      authService,
      onStage2Required = { inStage2 = true },
    )

    if (inStage2) {
      AuthStage2Presenter(
        authService,
        onBack = { inStage2 = false },
      )
    }
  }
}
