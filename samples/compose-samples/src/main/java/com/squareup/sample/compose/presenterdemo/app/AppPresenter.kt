package com.squareup.sample.compose.presenterdemo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.squareup.sample.compose.presenterdemo.app.auth.AuthFlowPresenter
import com.squareup.sample.compose.presenterdemo.app.auth.AuthService
import com.squareup.sample.compose.presenterdemo.structurednav.BackstackPresenter
import com.squareup.sample.compose.presenterdemo.structurednav.OverlaysPresenter

@Composable
fun AppPresenter(
  authService: AuthService,
  appletPresenter: AppletPresenter,
) {
  val isAuthorized by authService.isAuthorized.collectAsState()

  OverlaysPresenter(
    // Present view models from the following slots as overlays.
    DialogOverlaySlot,
    SheetOverlaySlot,
  ) {
    BackstackPresenter {
      if (!isAuthorized) {
        AuthFlowPresenter(authService)
      } else {
        appletPresenter()
      }
    }
  }
}
